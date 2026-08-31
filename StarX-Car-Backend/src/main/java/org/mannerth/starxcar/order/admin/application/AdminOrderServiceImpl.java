package org.mannerth.starxcar.order.admin.application;

import org.mannerth.starxcar.auth.domain.User;
import org.mannerth.starxcar.auth.infrastructure.UserMapper;
import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.driver.domain.Driver;
import org.mannerth.starxcar.driver.infrastructure.DriverMapper;
import org.mannerth.starxcar.order.admin.domain.AdminOperationLog;
import org.mannerth.starxcar.order.admin.domain.OrderOperationNote;
import org.mannerth.starxcar.order.admin.infrastructure.AdminOperationLogMapper;
import org.mannerth.starxcar.order.admin.infrastructure.OrderOperationNoteMapper;
import org.mannerth.starxcar.order.admin.response.AdminDriverLocationResponse;
import org.mannerth.starxcar.order.admin.response.AdminOrderFareItemResponse;
import org.mannerth.starxcar.order.admin.response.AdminOrderNoteResponse;
import org.mannerth.starxcar.order.admin.response.AdminOrderPageResponse;
import org.mannerth.starxcar.order.admin.response.AdminOrderSummaryMetricsResponse;
import org.mannerth.starxcar.order.admin.response.AdminOrderSummaryResponse;
import org.mannerth.starxcar.order.application.OrderAccessService;
import org.mannerth.starxcar.order.application.OrderQueryService;
import org.mannerth.starxcar.order.domain.DriverLocation;
import org.mannerth.starxcar.order.domain.OrderEventType;
import org.mannerth.starxcar.order.domain.OrderFareItem;
import org.mannerth.starxcar.order.domain.OrderOperatorType;
import org.mannerth.starxcar.order.domain.OrderStatus;
import org.mannerth.starxcar.order.domain.OrderType;
import org.mannerth.starxcar.order.domain.RideOrder;
import org.mannerth.starxcar.order.domain.RideOrderEvent;
import org.mannerth.starxcar.order.infrastructure.DriverLocationMapper;
import org.mannerth.starxcar.order.infrastructure.OrderFareItemMapper;
import org.mannerth.starxcar.order.infrastructure.RideOrderEventMapper;
import org.mannerth.starxcar.order.infrastructure.RideOrderMapper;
import org.mannerth.starxcar.order.response.OrderDetailResponse;
import org.mannerth.starxcar.order.response.OrderStatusTransitionResponse;
import org.mannerth.starxcar.passenger.domain.Passenger;
import org.mannerth.starxcar.passenger.infrastructure.PassengerMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;






@Service
public class AdminOrderServiceImpl implements AdminOrderService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final RideOrderMapper orderMapper;
    private final RideOrderEventMapper eventMapper;
    private final OrderFareItemMapper fareItemMapper;
    private final DriverLocationMapper locationMapper;
    private final OrderOperationNoteMapper noteMapper;
    private final AdminOperationLogMapper operationLogMapper;
    private final OrderAccessService accessService;
    private final OrderQueryService queryService;
    private final UserMapper userMapper;
    private final PassengerMapper passengerMapper;
    private final DriverMapper driverMapper;
    private final Clock clock;

    public AdminOrderServiceImpl(RideOrderMapper orderMapper, RideOrderEventMapper eventMapper,
                                 OrderFareItemMapper fareItemMapper, DriverLocationMapper locationMapper,
                                 OrderOperationNoteMapper noteMapper, AdminOperationLogMapper operationLogMapper,
                                 OrderAccessService accessService, OrderQueryService queryService, UserMapper userMapper,
                                 PassengerMapper passengerMapper, DriverMapper driverMapper, Clock clock) {
        this.orderMapper = orderMapper;
        this.eventMapper = eventMapper;
        this.fareItemMapper = fareItemMapper;
        this.locationMapper = locationMapper;
        this.noteMapper = noteMapper;
        this.operationLogMapper = operationLogMapper;
        this.accessService = accessService;
        this.queryService = queryService;
        this.userMapper = userMapper;
        this.passengerMapper = passengerMapper;
        this.driverMapper = driverMapper;
        this.clock = clock;
    }

    @Override
    public AdminOrderPageResponse list(long page, long size, String orderNo, OrderType orderType, OrderStatus status,
                                       String keyword, LocalDateTime createdFrom, LocalDateTime createdTo,
                                       LocalDateTime scheduledFrom, LocalDateTime scheduledTo) {
        validateRange(createdFrom, createdTo, "创建时间");
        validateRange(scheduledFrom, scheduledTo, "预约时间");
        LambdaQueryWrapper<RideOrder> query = baseQuery(orderType, status, createdFrom, createdTo, scheduledFrom, scheduledTo)
                .like(orderNo != null && !orderNo.isBlank(), RideOrder::getOrderNo, trim(orderNo));
        applyKeyword(query, trim(keyword));
        Page<RideOrder> result = orderMapper.selectPage(new Page<>(page, size), query.orderByDesc(RideOrder::getCreatedTime)
                .orderByDesc(RideOrder::getId));
        Map<Long, User> users = users(result.getRecords());
        Map<Long, Passenger> passengers = passengers(result.getRecords());
        Map<Long, Driver> drivers = drivers(result.getRecords());
        List<AdminOrderSummaryResponse> records = result.getRecords().stream()
                .map(order -> summary(order, users, passengers, drivers)).toList();
        return new AdminOrderPageResponse(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    @Override
    public AdminOrderSummaryMetricsResponse summary(LocalDateTime from, LocalDateTime to) {
        validateRange(from, to, "统计时间");
        List<RideOrder> orders = orderMapper.selectList(baseQuery(null, null, from, to, null, null));
        long total = orders.size();
        long instant = countType(orders, OrderType.INSTANT);
        long scheduled = countType(orders, OrderType.SCHEDULED);
        long waiting = count(orders, OrderStatus.SCHEDULED);
        long pending = count(orders, OrderStatus.PENDING);
        long accepted = count(orders, OrderStatus.ACCEPTED) + count(orders, OrderStatus.DRIVER_ARRIVED);
        long inService = count(orders, OrderStatus.IN_SERVICE);
        long completed = count(orders, OrderStatus.COMPLETED);
        long cancelled = count(orders, OrderStatus.CANCELLED);
        LocalDateTime atRiskBefore = now().plusMinutes(30);
        long atRisk = orders.stream().filter(order -> order.getOrderType() == OrderType.SCHEDULED
                        && (order.getStatus() == OrderStatus.SCHEDULED || order.getStatus() == OrderStatus.PENDING)
                        && order.getScheduledPickupTime() != null && !order.getScheduledPickupTime().isAfter(atRiskBefore))
                .count();
        BigDecimal completedFare = orders.stream().filter(order -> order.getStatus() == OrderStatus.COMPLETED)
                .map(RideOrder::getTotalFare).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AdminOrderSummaryMetricsResponse(total, instant, scheduled, waiting, pending, accepted, inService,
                completed, cancelled, atRisk, completedFare);
    }

    @Override
    public OrderDetailResponse detail(Long adminId, String orderNo) {
        return queryService.getOrder(adminId, UserType.admin, orderNo);
    }

    @Override
    public List<AdminOrderFareItemResponse> fareItems(String orderNo) {
        RideOrder order = accessService.requireByOrderNo(orderNo);
        return fareItemMapper.selectList(new LambdaQueryWrapper<OrderFareItem>().eq(OrderFareItem::getOrderId, order.getId())
                        .orderByAsc(OrderFareItem::getId)).stream()
                .map(item -> new AdminOrderFareItemResponse(item.getChargeType(), asInt(item.getRangeStart()),
                        asInt(item.getRangeEnd()), item.getActualUsage(), asInt(item.getUnitSize()), item.getUnitPrice(),
                        item.getChargedUnits(), item.getAmount())).toList();
    }

    @Override
    public List<AdminOrderNoteResponse> notes(String orderNo) {
        RideOrder order = accessService.requireByOrderNo(orderNo);
        List<OrderOperationNote> notes = noteMapper.selectList(new LambdaQueryWrapper<OrderOperationNote>()
                .eq(OrderOperationNote::getOrderId, order.getId()).orderByAsc(OrderOperationNote::getCreatedTime)
                .orderByAsc(OrderOperationNote::getId));
        Map<Long, User> admins = notes.isEmpty() ? Map.of() : userMapper.selectBatchIds(notes.stream()
                .map(OrderOperationNote::getAdminId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        return notes.stream().map(note -> new AdminOrderNoteResponse(note.getId(), note.getAdminId(),
                Optional.ofNullable(admins.get(note.getAdminId())).map(User::getUsername).orElse("管理员"),
                note.getContent(), note.getCreatedTime())).toList();
    }

    @Override
    public AdminDriverLocationResponse driverLocation(String orderNo) {
        RideOrder order = accessService.requireByOrderNo(orderNo);
        if (order.getDriverId() == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单尚未分配司机");
        DriverLocation location = locationMapper.selectById(order.getDriverId());
        if (location == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "司机暂无位置");
        return new AdminDriverLocationResponse(location.getLatitude(), location.getLongitude(), location.getAccuracyM(),
                location.getSpeedMps(), location.getBearing(), location.getRecordedTime(), location.getReceivedTime());
    }

    @Override
    @Transactional
    public OrderStatusTransitionResponse cancel(Long adminId, String orderNo, String reason) {
        RideOrder order = accessService.requireByOrderNo(orderNo);
        if (order.getStatus() != OrderStatus.SCHEDULED && order.getStatus() != OrderStatus.PENDING) throw conflict("仅等待派单或待抢订单可由管理员取消");
        LocalDateTime time = now();
        String normalized = required(reason, "取消原因");
        if (orderMapper.cancelOrder(order.getId(), order.getStatus(), order.getVersion(), time, OrderOperatorType.admin.name(), normalized) != 1) throw conflict("订单状态已变化");
        writeEvent(order, OrderEventType.CANCEL, OrderStatus.CANCELLED, adminId, time, normalized);
        writeLog(adminId, "ORDER_CANCEL", order, OrderStatus.CANCELLED, normalized, time);
        return new OrderStatusTransitionResponse(orderNo, OrderStatus.CANCELLED, time, null);
    }

    @Override
    @Transactional
    public OrderStatusTransitionResponse dispatchReservation(Long adminId, String orderNo, String reason) {
        RideOrder order = accessService.requireByOrderNo(orderNo);
        if (order.getOrderType() != OrderType.SCHEDULED || order.getStatus() != OrderStatus.SCHEDULED) throw conflict("仅等待派单的预约订单可提前派单");
        LocalDateTime time = now();
        String normalized = required(reason, "提前派单原因");
        if (orderMapper.dispatchReservation(order.getId(), order.getVersion(), time) != 1) throw conflict("订单状态已变化");
        writeEvent(order, OrderEventType.DISPATCH, OrderStatus.PENDING, adminId, time, normalized);
        writeLog(adminId, "RESERVATION_DISPATCH", order, OrderStatus.PENDING, normalized, time);
        return new OrderStatusTransitionResponse(orderNo, OrderStatus.PENDING, time, null);
    }

    @Override
    @Transactional
    public AdminOrderNoteResponse addNote(Long adminId, String orderNo, String content) {
        RideOrder order = accessService.requireByOrderNo(orderNo);
        LocalDateTime time = now();
        OrderOperationNote note = new OrderOperationNote();
        note.setOrderId(order.getId()); note.setAdminId(adminId); note.setContent(required(content, "运营备注")); note.setCreatedTime(time);
        if (noteMapper.insert(note) != 1 || note.getId() == null) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "保存运营备注失败");
        writeLog(adminId, "ORDER_NOTE_CREATE", order, order.getStatus(), note.getContent(), time);
        User admin = userMapper.selectById(adminId);
        return new AdminOrderNoteResponse(note.getId(), adminId, admin == null ? "管理员" : admin.getUsername(), note.getContent(), time);
    }

    private LambdaQueryWrapper<RideOrder> baseQuery(OrderType type, OrderStatus status, LocalDateTime createdFrom,
                                                      LocalDateTime createdTo, LocalDateTime scheduledFrom, LocalDateTime scheduledTo) {
        return new LambdaQueryWrapper<RideOrder>().eq(type != null, RideOrder::getOrderType, type)
                .eq(status != null, RideOrder::getStatus, status)
                .ge(createdFrom != null, RideOrder::getCreatedTime, createdFrom).le(createdTo != null, RideOrder::getCreatedTime, createdTo)
                .ge(scheduledFrom != null, RideOrder::getScheduledPickupTime, scheduledFrom).le(scheduledTo != null, RideOrder::getScheduledPickupTime, scheduledTo);
    }

    private void applyKeyword(LambdaQueryWrapper<RideOrder> query, String keyword) {
        if (keyword == null) return;
        Set<Long> passengerIds = matchingPassengerIds(keyword);
        Set<Long> driverIds = matchingDriverIds(keyword);
        query.and(group -> group.like(RideOrder::getOrderNo, keyword)
                .or(!passengerIds.isEmpty(), nested -> nested.in(RideOrder::getPassengerId, passengerIds))
                .or(!driverIds.isEmpty(), nested -> nested.in(RideOrder::getDriverId, driverIds)));
    }

    private Set<Long> matchingPassengerIds(String keyword) {
        Set<Long> ids = matchingUserIds(keyword);
        passengerMapper.selectList(new LambdaQueryWrapper<Passenger>().and(q -> q.like(Passenger::getPhoneNumber, keyword)
                .or().like(Passenger::getDisplayPhone, keyword))).forEach(passenger -> ids.add(passenger.getId()));
        return ids;
    }

    private Set<Long> matchingDriverIds(String keyword) {
        Set<Long> ids = matchingUserIds(keyword);
        driverMapper.selectList(new LambdaQueryWrapper<Driver>().and(q -> q.like(Driver::getPhoneNumber, keyword)
                .or().like(Driver::getDisplayPhone, keyword))).forEach(driver -> ids.add(driver.getId()));
        return ids;
    }

    private Set<Long> matchingUserIds(String keyword) {
        return userMapper.selectList(new LambdaQueryWrapper<User>().and(q -> q.like(User::getUsername, keyword)
                        .or().like(User::getRealName, keyword).or().like(User::getEmail, keyword))).stream()
                .map(User::getId).collect(Collectors.toSet());
    }

    private Map<Long, User> users(List<RideOrder> orders) {
        Set<Long> ids = new HashSet<>(); orders.forEach(order -> { ids.add(order.getPassengerId()); if (order.getDriverId() != null) ids.add(order.getDriverId()); });
        return ids.isEmpty() ? Map.of() : userMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(User::getId, user -> user));
    }
    private Map<Long, Passenger> passengers(List<RideOrder> orders) {
        Set<Long> ids = orders.stream().map(RideOrder::getPassengerId).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : passengerMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(Passenger::getId, item -> item));
    }
    private Map<Long, Driver> drivers(List<RideOrder> orders) {
        Set<Long> ids = orders.stream().map(RideOrder::getDriverId).filter(Objects::nonNull).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : driverMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(Driver::getId, item -> item));
    }
    private AdminOrderSummaryResponse summary(RideOrder order, Map<Long, User> users, Map<Long, Passenger> passengers, Map<Long, Driver> drivers) {
        User passengerUser = users.get(order.getPassengerId()); User driverUser = users.get(order.getDriverId());
        Passenger passenger = passengers.get(order.getPassengerId()); Driver driver = drivers.get(order.getDriverId());
        return new AdminOrderSummaryResponse(order.getOrderNo(), order.getOrderType(), order.getStatus(), order.getPickupName(), order.getDestinationName(),
                passengerUser == null ? null : passengerUser.getRealName(), passenger == null ? null : passenger.getDisplayPhone(),
                driverUser == null ? null : driverUser.getRealName(), driver == null ? null : driver.getDisplayPhone(),
                order.getScheduledPickupTime(), order.getDispatchTime(), order.getCreatedTime(), order.getAcceptedTime(), order.getCompletedTime(),
                order.getTotalFare(), order.getStatus() != OrderStatus.COMPLETED);
    }
    private long count(List<RideOrder> orders, OrderStatus status) { return orders.stream().filter(order -> order.getStatus() == status).count(); }
    private long countType(List<RideOrder> orders, OrderType type) { return orders.stream().filter(order -> order.getOrderType() == type).count(); }
    private void writeEvent(RideOrder order, OrderEventType eventType, OrderStatus target, Long adminId, LocalDateTime time, String reason) {
        RideOrderEvent event = new RideOrderEvent(); event.setOrderId(order.getId()); event.setEventType(eventType); event.setFromStatus(order.getStatus()); event.setToStatus(target); event.setOperatorType(OrderOperatorType.admin); event.setOperatorId(adminId); event.setRemark(reason); event.setCreatedTime(time);
        if (eventMapper.insert(event) != 1) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "记录订单事件失败");
    }
    private void writeLog(Long adminId, String action, RideOrder order, OrderStatus target, String reason, LocalDateTime time) {
        AdminOperationLog log = new AdminOperationLog(); log.setAdminId(adminId); log.setAction(action); log.setTargetType("ORDER"); log.setTargetId(order.getId()); log.setOrderId(order.getId()); log.setBeforeStatus(order.getStatus()); log.setAfterStatus(target); log.setReason(reason); log.setTraceId(MDC.get("traceId")); log.setCreatedTime(time);
        if (operationLogMapper.insert(log) != 1) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "记录管理员操作失败");
    }
    private void validateRange(LocalDateTime from, LocalDateTime to, String label) { if (from != null && to != null && to.isBefore(from)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "结束时间不能早于开始时间"); }
    private String required(String value, String label) { String result = trim(value); if (result == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "不能为空"); return result; }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private Integer asInt(Long value) { return value == null ? null : Math.toIntExact(value); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(Instant.now(clock), BUSINESS_ZONE).withNano(0); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}