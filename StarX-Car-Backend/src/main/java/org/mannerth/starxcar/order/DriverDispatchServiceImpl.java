package org.mannerth.starxcar.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.mannerth.starxcar.auth.User;
import org.mannerth.starxcar.auth.UserMapper;
import org.mannerth.starxcar.car.Car;
import org.mannerth.starxcar.car.CarMapper;
import org.mannerth.starxcar.config.DriverLocationProperties;
import org.mannerth.starxcar.driver.Driver;
import org.mannerth.starxcar.driver.DriverCarBinding;
import org.mannerth.starxcar.driver.DriverCarBindingMapper;
import org.mannerth.starxcar.driver.DriverMapper;
import org.mannerth.starxcar.order.response.OrderAcceptedResponse;
import org.mannerth.starxcar.order.response.OrderPageResponse;
import org.mannerth.starxcar.order.response.OrderSummaryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class DriverDispatchServiceImpl implements DriverDispatchService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<OrderStatus> ONGOING_ORDER_STATUSES = List.of(
            OrderStatus.ACCEPTED, OrderStatus.DRIVER_ARRIVED, OrderStatus.IN_SERVICE);

    private final DriverMapper driverMapper;
    private final UserMapper userMapper;
    private final CarMapper carMapper;
    private final DriverCarBindingMapper bindingMapper;
    private final DriverLocationMapper locationMapper;
    private final RideOrderMapper orderMapper;
    private final RideOrderEventMapper eventMapper;
    private final OrderAccessService orderAccessService;
    private final DriverLocationProperties locationProperties;
    private final Clock clock;

    public DriverDispatchServiceImpl(DriverMapper driverMapper, UserMapper userMapper, CarMapper carMapper,
                                     DriverCarBindingMapper bindingMapper, DriverLocationMapper locationMapper,
                                     RideOrderMapper orderMapper, RideOrderEventMapper eventMapper,
                                     OrderAccessService orderAccessService, DriverLocationProperties locationProperties,
                                     Clock clock) {
        this.driverMapper = driverMapper;
        this.userMapper = userMapper;
        this.carMapper = carMapper;
        this.bindingMapper = bindingMapper;
        this.locationMapper = locationMapper;
        this.orderMapper = orderMapper;
        this.eventMapper = eventMapper;
        this.orderAccessService = orderAccessService;
        this.locationProperties = locationProperties;
        this.clock = clock;
    }

    @Override
    public OrderPageResponse listPendingOrders(Long driverId, long page, long size) {
        requireEligibleOnlineDriver(driverId);
        IPage<RideOrder> result = orderMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<RideOrder>()
                .eq(RideOrder::getStatus, OrderStatus.PENDING)
                .isNull(RideOrder::getDriverId)
                .orderByDesc(RideOrder::getCreatedTime)
                .orderByDesc(RideOrder::getId));
        List<OrderSummaryResponse> records = result.getRecords().stream().map(order -> new OrderSummaryResponse(
                order.getOrderNo(), order.getStatus(), order.getOrderType(), order.getScheduledPickupTime(),
                order.getPickupName(), order.getDestinationName(),
                order.getTotalFare(), order.getCreatedTime(), true)).toList();
        return new OrderPageResponse(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    @Override
    @Transactional
    public OrderAcceptedResponse acceptOrder(Long driverId, String orderNo) {
        Driver driver = requireEligibleOnlineDriver(driverId);
        if (ongoingOrderCount(driverId) > 0) {
            throw conflict("司机存在进行中订单");
        }
        RideOrder order = orderAccessService.requireByOrderNo(orderNo);
        if (order.getStatus() != OrderStatus.PENDING || order.getDriverId() != null) {
            throw conflict("订单当前不可抢");
        }

        LocalDateTime acceptedTime = now();
        int accepted = orderMapper.acceptPendingOrder(order.getId(), driverId, driver.getCarId(), acceptedTime,
                order.getVersion());
        if (accepted != 1) {
            throw conflict("订单已被其他司机抢走或状态已变化");
        }

        RideOrderEvent event = new RideOrderEvent();
        event.setOrderId(order.getId());
        event.setEventType(OrderEventType.ACCEPT);
        event.setFromStatus(OrderStatus.PENDING);
        event.setToStatus(OrderStatus.ACCEPTED);
        event.setOperatorType(OrderOperatorType.driver);
        event.setOperatorId(driverId);
        event.setCreatedTime(acceptedTime);
        if (eventMapper.insert(event) != 1) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "记录接单事件失败");
        }
        if (driverMapper.markBusyIfOnline(driverId) != 1) {
            throw conflict("司机出车状态已变化");
        }
        if (locationMapper.attachActiveOrder(driverId, order.getId(), freshSince(acceptedTime)) != 1) {
            throw conflict("司机位置已过期或已关联其他订单");
        }
        return new OrderAcceptedResponse(order.getOrderNo(), OrderStatus.ACCEPTED, driver.getCarId(), acceptedTime);
    }

    private Driver requireEligibleOnlineDriver(Long driverId) {
        Driver driver = driverMapper.selectById(driverId);
        if (driver == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "司机不存在");
        }
        User user = userMapper.selectById(driverId);
        if (user == null || !"active".equals(user.getStatus()) || !"approved".equals(driver.getApprovalStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "司机账号不可用或尚未审核通过");
        }
        if (!"online".equals(driver.getWorkStatus())) {
            throw conflict("司机当前未处于听单状态");
        }
        if (driver.getCarId() == null) {
            throw conflict("请先设置当前用车");
        }
        Car car = carMapper.selectById(driver.getCarId());
        if (car == null || !"active".equals(car.getVehicleStatus())) {
            throw conflict("当前用车不可用");
        }
        if (!bindingMapper.exists(Wrappers.<DriverCarBinding>lambdaQuery()
                .eq(DriverCarBinding::getDriverId, driverId)
                .eq(DriverCarBinding::getCarId, driver.getCarId())
                .eq(DriverCarBinding::getStatus, "active"))) {
            throw conflict("当前用车未有效绑定到司机");
        }
        DriverLocation location = locationMapper.selectById(driverId);
        if (location == null || location.getReceivedTime() == null || location.getReceivedTime().isBefore(freshSince(now()))
                || location.getActiveOrderId() != null) {
            throw conflict("司机没有可用的最新位置");
        }
        return driver;
    }

    private long ongoingOrderCount(Long driverId) {
        return orderMapper.selectCount(Wrappers.<RideOrder>lambdaQuery()
                .eq(RideOrder::getDriverId, driverId)
                .in(RideOrder::getStatus, ONGOING_ORDER_STATUSES));
    }

    private LocalDateTime freshSince(LocalDateTime time) {
        return time.minusSeconds(locationProperties.getFreshnessSeconds());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(Instant.now(clock), BUSINESS_ZONE).withNano(0);
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
