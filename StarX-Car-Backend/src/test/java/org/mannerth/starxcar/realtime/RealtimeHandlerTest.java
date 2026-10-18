package org.mannerth.starxcar.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mannerth.starxcar.auth.domain.User;
import org.mannerth.starxcar.auth.infrastructure.UserMapper;
import org.mannerth.starxcar.config.JwtProperties;
import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.driver.application.DriverWorkService;
import org.mannerth.starxcar.order.application.OrderQueryService;
import org.mannerth.starxcar.order.application.PassengerTripLocationService;
import org.mannerth.starxcar.order.response.DriverLocationResponse;
import org.mannerth.starxcar.order.response.OrderDetailResponse;
import org.mannerth.starxcar.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RealtimeHandlerTest {
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    final OrderQueryService orders = mock(OrderQueryService.class);
    final PassengerTripLocationService locations = mock(PassengerTripLocationService.class);
    final DriverWorkService work = mock(DriverWorkService.class);
    final UserMapper users = mock(UserMapper.class);
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    JwtService jwt;
    RealtimeHandler handler;

    @BeforeEach void setup() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-realtime-secret"); properties.setExpire(3600);
        jwt = new JwtService(properties);
        handler = new RealtimeHandler(json, jwt, orders, locations, work, validation.getValidator(), users,
                new org.mannerth.starxcar.config.DriverLocationProperties());
        when(users.selectById(anyLong())).thenAnswer(call -> {
            User user = new User(); user.setId(call.getArgument(0));
            user.setType(UserType.passenger); user.setStatus("active"); return user;
        });
    }
    @AfterEach void cleanup() { handler.shutdown(); validation.close(); }

    @Test void rejectsUnauthenticatedAndRefreshTokens() throws Exception {
        Client anonymous = client("anonymous");
        send(anonymous, Map.of("type", "subscribe", "topic", "order.detail", "orderNo", "ORD-1"));
        assertThat(anonymous.frames.getLast().path("code").asInt()).isEqualTo(401);
        verify(anonymous.session).close(CloseStatus.POLICY_VIOLATION);
        Client refresh = client("refresh");
        send(refresh, Map.of("type", "auth", "token", jwt.createToken("7", Map.of("role", "passenger", "token_type", "refresh"))));
        assertThat(refresh.frames.getLast().path("code").asInt()).isEqualTo(401);
        verifyNoInteractions(orders, work);
    }

    @Test void validatesOwnershipAndPublishesCommittedLatestSnapshot() throws Exception {
        Client passenger = login("passenger", 7, "passenger");
        when(orders.getOrder(7L, UserType.passenger, "FOREIGN"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        subscribe(passenger, "order.detail", "FOREIGN");
        assertThat(passenger.frames.getLast().path("type").asText()).isEqualTo("subscription.error");
        assertThat(passenger.frames.getLast().path("code").asInt()).isEqualTo(404);
        when(orders.getOrder(7L, UserType.passenger, "ORD-1")).thenReturn(detail("ACCEPTED", 99));
        subscribe(passenger, "order.detail", "ORD-1");
        assertThat(passenger.frames.getLast().path("data").path("status").asText()).isEqualTo("ACCEPTED");
        CountDownLatch changed = new CountDownLatch(1);
        passenger.onFrame = frame -> { if (frame.path("data").path("status").asText().equals("COMPLETED")) changed.countDown(); };
        when(orders.getOrder(7L, UserType.passenger, "ORD-1")).thenReturn(detail("COMPLETED", 99));
        handler.ordersChanged();
        assertThat(changed.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(passenger.frames).anyMatch(frame -> frame.path("type").asText().equals("orders.changed"));
    }

    @Test void sendsLocationOnlyToTheOrdersAssignedDriverSubscription() throws Exception {
        Client first = login("first", 7, "passenger");
        Client second = login("second", 8, "passenger");
        when(orders.getOrder(7L, UserType.passenger, "ORD-1")).thenReturn(detail("ACCEPTED", 99));
        when(orders.getOrder(8L, UserType.passenger, "ORD-2")).thenReturn(detail("ACCEPTED", 100));
        DriverLocationResponse location = json.readValue("{\"latitude\":31.2,\"longitude\":121.4,\"stale\":false}", DriverLocationResponse.class);
        when(locations.getDriverLocation(anyLong(), anyString())).thenReturn(location);
        subscribe(first, "order.location", "ORD-1"); subscribe(second, "order.location", "ORD-2");
        CountDownLatch update = new CountDownLatch(1);
        first.onFrame = frame -> { if (frame.path("type").asText().equals("snapshot")) update.countDown(); };
        handler.locationChanged(99L);
        assertThat(update.await(3, TimeUnit.SECONDS)).isTrue();
        verify(locations, times(2)).getDriverLocation(7L, "ORD-1");
        verify(locations, times(1)).getDriverLocation(8L, "ORD-2");
        send(first, Map.of("type", "unsubscribe", "topic", "order.location", "orderNo", "ORD-1"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"order.route", "order.location"})
    void stateTransitionClosesOnlyTheObsoleteSubscriptionNormally(String topic) throws Exception {
        Client passenger = login("passenger", 7, "passenger");
        when(orders.getOrder(7L, UserType.passenger, "ORD-1")).thenReturn(detail("IN_SERVICE", 99));
        if (topic.equals("order.route")) {
            when(locations.getRouteContext(7L, "ORD-1")).thenReturn(json.readValue(
                    "{\"driverLocation\":{\"latitude\":31.2,\"longitude\":121.4,\"stale\":true}}",
                    org.mannerth.starxcar.order.response.RideRouteContextResponse.class));
        } else {
            when(locations.getDriverLocation(7L, "ORD-1")).thenReturn(json.readValue(
                    "{\"latitude\":31.2,\"longitude\":121.4,\"stale\":true}", DriverLocationResponse.class));
        }
        subscribe(passenger, "order.detail", "ORD-1");
        subscribe(passenger, topic, "ORD-1");
        if (topic.equals("order.route")) {
            when(locations.getRouteContext(7L, "ORD-1"))
                    .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "订单当前不可获取路线规划上下文"));
        } else {
            when(locations.getDriverLocation(7L, "ORD-1"))
                    .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "订单当前不可查看司机位置"));
        }
        CountDownLatch closed = new CountDownLatch(1);
        passenger.onFrame = frame -> {
            if (frame.path("type").asText().equals("subscription.closed") && frame.path("topic").asText().equals(topic)) closed.countDown();
        };
        handler.ordersChanged();
        assertThat(closed.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(passenger.frames).noneMatch(frame -> frame.path("type").asText().equals("subscription.error"));
        verify(passenger.session, never()).close(any());

        CountDownLatch detailUpdated = new CountDownLatch(1);
        passenger.onFrame = frame -> {
            if (frame.path("data").path("status").asText().equals("COMPLETED")) detailUpdated.countDown();
        };
        when(orders.getOrder(7L, UserType.passenger, "ORD-1")).thenReturn(detail("COMPLETED", 99));
        handler.ordersChanged();
        assertThat(detailUpdated.await(3, TimeUnit.SECONDS)).isTrue();
        if (topic.equals("order.route")) verify(locations, times(2)).getRouteContext(7L, "ORD-1");
        else verify(locations, times(2)).getDriverLocation(7L, "ORD-1");
    }

    @Test void subscribingAfterServiceEndsClosesNormallyButForeignOrdersRemainErrors() throws Exception {
        Client passenger = login("passenger", 7, "passenger");
        when(orders.getOrder(7L, UserType.passenger, "ORD-1")).thenReturn(detail("COMPLETED", 99));
        when(locations.getRouteContext(7L, "ORD-1"))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "订单当前不可获取路线规划上下文"));
        subscribe(passenger, "order.route", "ORD-1");
        assertThat(passenger.frames.getLast().path("type").asText()).isEqualTo("subscription.closed");
        when(orders.getOrder(7L, UserType.passenger, "FOREIGN"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        subscribe(passenger, "order.route", "FOREIGN");
        assertThat(passenger.frames.getLast().path("type").asText()).isEqualTo("subscription.error");
        assertThat(passenger.frames.getLast().path("code").asInt()).isEqualTo(404);
    }

    @Test void marksStoppedLocationsStaleFromMemoryWithoutPolling() throws Exception {
        Client passenger = login("passenger", 7, "passenger");
        when(orders.getOrder(7L, UserType.passenger, "ORD-1")).thenReturn(detail("ACCEPTED", 99));
        var oldTime = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).minusSeconds(31);
        var fix = new DriverLocationResponse(new java.math.BigDecimal("31.2"), new java.math.BigDecimal("121.4"),
                null, null, null, oldTime, oldTime, false);
        when(locations.getDriverLocation(7L, "ORD-1")).thenReturn(fix);
        subscribe(passenger, "order.location", "ORD-1");
        clearInvocations(orders, locations, users);
        handler.expireConnections();
        assertThat(passenger.frames.getLast().path("data").path("stale").asBoolean()).isTrue();
        verifyNoInteractions(orders, locations, users);
        int count = passenger.frames.size();
        handler.expireConnections();
        assertThat(passenger.frames).hasSize(count);
    }

    @Test void validatesLocationFramesAndBindsDriverIdentityToJwt() throws Exception {
        when(users.selectById(99L)).thenAnswer(call -> {
            User user = new User(); user.setType(UserType.driver); user.setStatus("active"); return user;
        });
        Client driver = login("driver", 99, "driver");
        send(driver, Map.of("type", "driver.location", "id", "bad", "data", Map.of("latitude", 200)));
        assertThat(driver.frames.getLast().path("code").asInt()).isEqualTo(400);
        verifyNoInteractions(work);
        send(driver, Map.of("type", "driver.location", "id", "fix", "data", Map.of(
                "driverId", 1234, "latitude", 31.2, "longitude", 121.4, "accuracyM", 8.5,
                "recordedTime", "2026-10-18 12:00:00")));
        verify(work).reportLocation(eq(99L), any());
        assertThat(driver.frames.getLast().path("type").asText()).isEqualTo("ack");
        assertThat(driver.frames.getLast().path("id").asText()).isEqualTo("fix");
    }

    @Test void revocationAndRoleRestrictionsApplyToSocketFrames() throws Exception {
        Client passenger = login("passenger", 7, "passenger");
        send(passenger, Map.of("type", "driver.location", "data", Map.of()));
        assertThat(passenger.frames.getLast().path("code").asInt()).isEqualTo(403);
        verifyNoInteractions(work);
        Client revoked = login("revoked", 8, "passenger");
        User disabled = new User(); disabled.setType(UserType.passenger); disabled.setStatus("disabled");
        when(users.selectById(8L)).thenReturn(disabled);
        send(revoked, Map.of("type", "ping"));
        assertThat(revoked.frames.getLast().path("code").asInt()).isEqualTo(403);
        verify(revoked.session).close(CloseStatus.NOT_ACCEPTABLE);
    }

    OrderDetailResponse detail(String status, long driverId) throws Exception {
        return json.readValue("{\"orderNo\":\"ORD-1\",\"status\":\"" + status
                + "\",\"driver\":{\"driverId\":" + driverId + "},\"events\":[]}", OrderDetailResponse.class);
    }
    Client login(String name, long userId, String role) throws Exception {
        Client client = client(name);
        send(client, Map.of("type", "auth", "token", jwt.createToken(Long.toString(userId), Map.of("role", role, "token_type", "access"))));
        assertThat(client.frames.getLast().path("type").asText()).isEqualTo("ready");
        return client;
    }
    void subscribe(Client client, String topic, String orderNo) throws Exception { send(client, Map.of("type", "subscribe", "topic", topic, "orderNo", orderNo)); }
    void send(Client client, Object frame) throws Exception { handler.handleTextMessage(client.session, new TextMessage(json.writeValueAsString(frame))); }
    Client client(String name) throws Exception {
        Client client = new Client(); when(client.session.getId()).thenReturn(name); when(client.session.isOpen()).thenReturn(true);
        doAnswer(call -> {
            JsonNode frame = json.readTree(((TextMessage) call.getArgument(0)).getPayload());
            client.frames.add(frame); client.onFrame.accept(frame); return null;
        }).when(client.session).sendMessage(any());
        handler.afterConnectionEstablished(client.session); return client;
    }
    static class Client {
        final WebSocketSession session = mock(WebSocketSession.class);
        final List<JsonNode> frames = new CopyOnWriteArrayList<>();
        volatile java.util.function.Consumer<JsonNode> onFrame = frame -> {};
    }
}
