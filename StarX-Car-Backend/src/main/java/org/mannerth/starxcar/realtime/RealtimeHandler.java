package org.mannerth.starxcar.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Validator;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.mannerth.starxcar.auth.infrastructure.UserMapper;
import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.config.DriverLocationProperties;
import org.mannerth.starxcar.order.response.DriverLocationResponse;
import org.mannerth.starxcar.order.response.RideRouteContextResponse;
import org.mannerth.starxcar.driver.application.DriverWorkService;
import org.mannerth.starxcar.driver.request.DriverLocationRequest;
import org.mannerth.starxcar.order.application.OrderQueryService;
import org.mannerth.starxcar.order.application.PassengerTripLocationService;
import org.mannerth.starxcar.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Authenticated, replayable order snapshots; never accepts a caller-supplied user ID. */
@Component
public class RealtimeHandler extends TextWebSocketHandler {
    private static final Set<String> TOPICS = Set.of("order.detail", "order.location", "order.route");
    private final ObjectMapper json;
    private final JwtService jwt;
    private final OrderQueryService orders;
    private final PassengerTripLocationService locations;
    private final DriverWorkService work;
    private final Validator validator;
    private final UserMapper users;
    private final DriverLocationProperties locationProperties;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    // Coalesce bursts to latest snapshots; at most one drain is queued, with no lost order-change signal.
    private final java.util.concurrent.ExecutorService events = java.util.concurrent.Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("realtime-events").factory());
    private final AtomicBoolean draining = new AtomicBoolean();
    private final AtomicBoolean ordersDirty = new AtomicBoolean();
    private final Set<Long> dirtyDrivers = ConcurrentHashMap.newKeySet();

    public RealtimeHandler(ObjectMapper json, JwtService jwt, OrderQueryService orders,
                           PassengerTripLocationService locations, DriverWorkService work, Validator validator, UserMapper users,
                           DriverLocationProperties locationProperties) {
        this.json = json; this.jwt = jwt; this.orders = orders;
        this.locations = locations; this.work = work; this.validator = validator; this.users = users;
        this.locationProperties = locationProperties;
    }

    @Override public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(16 * 1024);
        clients.put(session.getId(), new Client(new ConcurrentWebSocketSessionDecorator(session, 5000, 128 * 1024)));
    }

    @Override public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        Client client = clients.get(session.getId());
        if (client == null) return;
        String id = "";
        JsonNode frame = null;
        try {
            frame = json.readTree(message.getPayload());
            if (frame == null || !frame.isObject()) throw badRequest();
            id = frame.path("id").asText("");
            String type = frame.path("type").asText();
            if ("auth".equals(type)) {
                Claims claims = jwt.parseToken(frame.path("token").asText()).orElseThrow(this::unauthorized);
                if (!"access".equals(claims.get("token_type", String.class))) throw unauthorized();
                client.userId = Long.valueOf(claims.getSubject());
                String role = claims.get("role", String.class);
                if (role == null || client.userId <= 0) throw unauthorized();
                client.role = UserType.valueOf(role.toLowerCase(java.util.Locale.ROOT));
                client.expires = claims.getExpiration().toInstant();
                requireActiveUser(client);
                client.subscriptions.clear();
                send(client, Map.of("type", "ready"));
                return;
            }
            requireAuthenticated(client);
            requireActiveUser(client);
            client.lastSeen = Instant.now();
            switch (type) {
                case "ping" -> send(client, Map.of("type", "pong"));
                case "subscribe" -> {
                    String topic = frame.path("topic").asText();
                    String orderNo = frame.path("orderNo").asText();
                    if (!TOPICS.contains(topic) || orderNo.isBlank() || orderNo.length() > 64
                            || client.subscriptions.size() >= 16) throw badRequest();
                    if (client.role == UserType.admin || (!topic.equals("order.detail") && client.role != UserType.passenger)) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权订阅该主题");
                    }
                    // Ownership check also supplies the assigned driver used for location fan-out.
                    var detail = orders.getOrder(client.userId, client.role, orderNo);
                    Subscription sub = new Subscription(topic, orderNo,
                            detail.driver() == null ? null : detail.driver().driverId());
                    client.subscriptions.put(topic + ":" + orderNo, sub);
                    snapshot(client, sub);
                }
                case "unsubscribe" -> client.subscriptions.remove(frame.path("topic").asText() + ":" + frame.path("orderNo").asText());
                case "driver.location" -> {
                    if (client.role != UserType.driver) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                    DriverLocationRequest request = json.treeToValue(frame.path("data"), DriverLocationRequest.class);
                    if (!validator.validate(request).isEmpty()) throw badRequest();
                    work.reportLocation(client.userId, request);
                    send(client, Map.of("type", "ack", "id", id));
                }
                default -> throw badRequest();
            }
        } catch (ResponseStatusException error) {
            if (frame != null && "subscribe".equals(frame.path("type").asText())) {
                send(client, Map.of("type", "subscription.error", "topic", frame.path("topic").asText(),
                        "orderNo", frame.path("orderNo").asText(), "code", error.getStatusCode().value(),
                        "message", error.getReason() == null ? "订阅失败" : error.getReason()));
            } else {
                send(client, Map.of("type", "error", "id", id, "code", error.getStatusCode().value(),
                        "message", error.getReason() == null ? "请求失败" : error.getReason()));
            }
            if (error.getStatusCode().value() == 401) close(client, CloseStatus.POLICY_VIOLATION);
            if (error.getStatusCode().value() == 403) close(client, CloseStatus.NOT_ACCEPTABLE);
        } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException error) {
            send(client, Map.of("type", "error", "id", id, "code", 400, "message", "消息格式错误"));
        } catch (Exception error) {
            send(client, Map.of("type", "error", "id", id, "code", 500, "message", "实时请求失败，请重试"));
        }
    }

    private void snapshot(Client client, Subscription sub) {
        synchronized (sub) {
        try {
            requireAuthenticated(client);
            requireActiveUser(client);
            Object data = switch (sub.topic) {
                case "order.detail" -> orders.getOrder(client.userId, client.role, sub.orderNo);
                case "order.location" -> locations.getDriverLocation(client.userId, sub.orderNo);
                case "order.route" -> locations.getRouteContext(client.userId, sub.orderNo);
                default -> throw badRequest();
            };
            send(client, Map.of("type", "snapshot", "topic", sub.topic, "orderNo", sub.orderNo, "data", data));
            DriverLocationResponse location = data instanceof DriverLocationResponse fix ? fix
                    : data instanceof RideRouteContextResponse route ? route.driverLocation() : null;
            sub.staleAt = null;
            if (location != null && !location.stale() && location.receivedTime() != null) {
                sub.staleAt = location.receivedTime().atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant()
                        .plusSeconds(locationProperties.getFreshnessSeconds());
                sub.staleSnapshot = json.valueToTree(data);
            }
        } catch (ResponseStatusException error) {
            sub.staleAt = null;
            if (!sub.topic.equals("order.detail") && error.getStatusCode().value() == 409) {
                // A completed/cancelled trip no longer exposes a live location/route.
                // End that subscription normally while leaving the detail subscription and socket alive.
                client.subscriptions.remove(sub.topic + ":" + sub.orderNo, sub);
                send(client, Map.of("type", "subscription.closed", "topic", sub.topic, "orderNo", sub.orderNo,
                        "reason", "resource_state_changed", "code", 409));
                return;
            }
            send(client, Map.of("type", "subscription.error", "topic", sub.topic, "orderNo", sub.orderNo,
                    "code", error.getStatusCode().value(), "transient", !sub.topic.equals("order.detail") && error.getStatusCode().value() == 404,
                    "message", error.getReason() == null ? "订阅暂不可用" : error.getReason()));
            if (error.getStatusCode().value() == 401 || error.getStatusCode().value() == 403) {
                close(client, error.getStatusCode().value() == 401 ? CloseStatus.POLICY_VIOLATION : CloseStatus.NOT_ACCEPTABLE);
            }
        } catch (Exception error) {
            sub.staleAt = null;
            send(client, Map.of("type", "subscription.error", "topic", sub.topic, "orderNo", sub.orderNo,
                    "code", 503, "message", "实时数据暂不可用"));
        }
        }
    }

    public void ordersChanged() { ordersDirty.set(true); scheduleDrain(); }
    public void locationChanged(Long driverId) { dirtyDrivers.add(driverId); scheduleDrain(); }

    private void scheduleDrain() {
        if (!events.isShutdown() && draining.compareAndSet(false, true)) events.execute(() -> {
            try {
                do {
                    if (ordersDirty.getAndSet(false)) publishOrders();
                    for (Long driverId : dirtyDrivers.toArray(Long[]::new)) {
                        if (dirtyDrivers.remove(driverId)) publishLocation(driverId);
                    }
                } while (ordersDirty.get() || !dirtyDrivers.isEmpty());
            } finally {
                draining.set(false);
                if (ordersDirty.get() || !dirtyDrivers.isEmpty()) scheduleDrain();
            }
        });
    }

    private void publishOrders() {
        clients.values().forEach(client -> {
            if (!authenticated(client)) return;
            send(client, Map.of("type", "orders.changed"));
            client.subscriptions.values().forEach(sub -> {
                try {
                    var detail = orders.getOrder(client.userId, client.role, sub.orderNo);
                    sub.driverId = detail.driver() == null ? null : detail.driver().driverId();
                } catch (Exception ignored) { sub.driverId = null; }
                snapshot(client, sub);
            });
        });
    }

    private void publishLocation(Long driverId) {
        clients.values().forEach(client -> {
            if (!authenticated(client)) return;
            if (client.role == UserType.admin) send(client, Map.of("type", "location.changed"));
            client.subscriptions.values().stream()
                    .filter(sub -> !sub.topic.equals("order.detail") && driverId.equals(sub.driverId))
                    .forEach(sub -> snapshot(client, sub));
        });
    }

    @Scheduled(fixedDelay = 5000) public void expireConnections() {
        Instant now = Instant.now();
        clients.values().forEach(client -> {
            if ((client.userId == null && client.created.plusSeconds(10).isBefore(now))
                    || (client.userId != null && (!authenticated(client) || client.lastSeen.plusSeconds(75).isBefore(now)))) {
                close(client, CloseStatus.POLICY_VIOLATION);
            } else if (authenticated(client)) {
                // Mark the last known fix stale once, from memory, even when the driver stops reporting.
                // This timer never queries location/order tables.
                client.subscriptions.values().forEach(sub -> {
                    synchronized (sub) {
                        if (sub.staleAt != null && !sub.staleAt.isAfter(now)) {
                            sub.staleAt = null;
                            JsonNode data = sub.staleSnapshot.deepCopy();
                            JsonNode location = sub.topic.equals("order.route") ? data.path("driverLocation") : data;
                            ((com.fasterxml.jackson.databind.node.ObjectNode) location).put("stale", true);
                            send(client, Map.of("type", "snapshot", "topic", sub.topic, "orderNo", sub.orderNo, "data", data));
                        }
                    }
                });
            }
        });
    }

    private boolean authenticated(Client client) { return client.userId != null && client.expires.isAfter(Instant.now()); }
    private void requireActiveUser(Client client) {
        var user = users.selectById(client.userId);
        if (user == null || user.getType() != client.role || !"active".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不可用");
        }
    }
    private void requireAuthenticated(Client client) { if (!authenticated(client)) throw unauthorized(); }
    private ResponseStatusException unauthorized() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态已过期"); }
    private ResponseStatusException badRequest() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "实时请求参数错误"); }
    private void send(Client client, Object frame) {
        try { if (client.session.isOpen()) client.session.sendMessage(new TextMessage(json.writeValueAsString(frame))); }
        catch (Exception error) { close(client, CloseStatus.SERVER_ERROR); }
    }
    private void close(Client client, CloseStatus status) {
        clients.remove(client.session.getId());
        try { client.session.close(status); } catch (IOException ignored) { }
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { clients.remove(session.getId()); }
    @Override public void handleTransportError(WebSocketSession session, Throwable error) {
        Client client = clients.get(session.getId()); if (client != null) close(client, CloseStatus.SERVER_ERROR);
    }
    @PreDestroy public void shutdown() { events.shutdownNow(); clients.values().forEach(c -> close(c, CloseStatus.GOING_AWAY)); }

    private static final class Client {
        final WebSocketSession session;
        final Instant created = Instant.now();
        final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
        volatile Long userId;
        volatile UserType role;
        volatile Instant expires = Instant.EPOCH;
        volatile Instant lastSeen = Instant.now();
        Client(WebSocketSession session) { this.session = session; }
    }
    private static final class Subscription {
        final String topic;
        final String orderNo;
        volatile Long driverId;
        Instant staleAt;
        JsonNode staleSnapshot;
        Subscription(String topic, String orderNo, Long driverId) { this.topic = topic; this.orderNo = orderNo; this.driverId = driverId; }
    }
}
