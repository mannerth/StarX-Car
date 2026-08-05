package org.mannerth.starxcar.driver;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.mannerth.starxcar.auth.User;
import org.mannerth.starxcar.auth.UserMapper;
import org.mannerth.starxcar.car.Car;
import org.mannerth.starxcar.car.CarMapper;
import org.mannerth.starxcar.config.DriverLocationProperties;
import org.mannerth.starxcar.driver.request.DriverLocationRequest;
import org.mannerth.starxcar.order.DriverLocation;
import org.mannerth.starxcar.order.DriverLocationMapper;
import org.mannerth.starxcar.order.OrderStatus;
import org.mannerth.starxcar.order.RideOrder;
import org.mannerth.starxcar.order.RideOrderMapper;
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
public class DriverWorkService {
    private static final List<OrderStatus> ONGOING_ORDER_STATUSES = List.of(
            OrderStatus.ACCEPTED, OrderStatus.DRIVER_ARRIVED, OrderStatus.IN_SERVICE);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final DriverMapper driverMapper;
    private final UserMapper userMapper;
    private final CarMapper carMapper;
    private final DriverCarBindingMapper driverCarBindingMapper;
    private final RideOrderMapper rideOrderMapper;
    private final DriverLocationMapper driverLocationMapper;
    private final DriverLocationProperties locationProperties;
    private final Clock clock;

    public DriverWorkService(DriverMapper driverMapper, UserMapper userMapper, CarMapper carMapper,
                             DriverCarBindingMapper driverCarBindingMapper, RideOrderMapper rideOrderMapper,
                             DriverLocationMapper driverLocationMapper, DriverLocationProperties locationProperties,
                             Clock clock) {
        this.driverMapper = driverMapper;
        this.userMapper = userMapper;
        this.carMapper = carMapper;
        this.driverCarBindingMapper = driverCarBindingMapper;
        this.rideOrderMapper = rideOrderMapper;
        this.driverLocationMapper = driverLocationMapper;
        this.locationProperties = locationProperties;
        this.clock = clock;
    }

    @Transactional
    public Driver updateWorkStatus(Long driverId, String status) {
        Driver driver = requireActiveDriver(driverId);
        long ongoingOrders = ongoingOrderCount(driverId);
        if ("offline".equals(status)) {
            if (ongoingOrders > 0 || "busy".equals(driver.getWorkStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "存在进行中订单，无法下线");
            }
            driver.setWorkStatus("offline");
        } else {
            if ("busy".equals(driver.getWorkStatus()) || ongoingOrders > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "存在进行中订单，无法切换听单状态");
            }
            requireCurrentActiveCar(driver);
            driver.setWorkStatus("online");
        }
        driverMapper.updateById(driver);
        return driver;
    }

    public void reportLocation(Long driverId, DriverLocationRequest request) {
        Driver driver = requireActiveDriver(driverId);
        if (!"online".equals(driver.getWorkStatus()) && !"busy".equals(driver.getWorkStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "司机当前未出车，不能上报位置");
        }
        LocalDateTime now = now();
        if (request.recordedTime().isAfter(now.plusSeconds(locationProperties.getMaxRecordedTimeFutureSeconds()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "设备定位时间不能超过当前时间允许范围");
        }
        DriverLocation location = new DriverLocation();
        location.setDriverId(driverId);
        location.setLatitude(request.latitude());
        location.setLongitude(request.longitude());
        location.setAccuracyM(request.accuracyM());
        location.setSpeedMps(request.speedMps());
        location.setBearing(request.bearing());
        location.setRecordedTime(request.recordedTime());
        location.setReceivedTime(now);
        int affectedRows = driverLocationMapper.upsertLatestLocation(location);
        // MySQL reports 0 when an ON DUPLICATE KEY UPDATE writes values identical to the existing row.
        // A repeated position report is therefore a successful, idempotent update.
        if (affectedRows < 0 || affectedRows > 2) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "位置上报失败");
        }
    }

    private Driver requireActiveDriver(Long driverId) {
        Driver driver = driverMapper.selectById(driverId);
        if (driver == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "司机不存在");
        }
        User user = userMapper.selectById(driverId);
        if (user == null || !"active".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已停用或注销");
        }
        if (!"approved".equals(driver.getApprovalStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "司机尚未审核通过");
        }
        return driver;
    }

    private void requireCurrentActiveCar(Driver driver) {
        if (driver.getCarId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先设置当前用车");
        }
        Car car = carMapper.selectById(driver.getCarId());
        if (car == null || !"active".equals(car.getVehicleStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前用车不可用");
        }
        boolean bound = driverCarBindingMapper.exists(Wrappers.<DriverCarBinding>lambdaQuery()
                .eq(DriverCarBinding::getDriverId, driver.getId())
                .eq(DriverCarBinding::getCarId, driver.getCarId())
                .eq(DriverCarBinding::getStatus, "active"));
        if (!bound) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前用车未有效绑定到司机");
        }
    }

    private long ongoingOrderCount(Long driverId) {
        return rideOrderMapper.selectCount(Wrappers.<RideOrder>lambdaQuery()
                .eq(RideOrder::getDriverId, driverId)
                .in(RideOrder::getStatus, ONGOING_ORDER_STATUSES));
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(Instant.now(clock), BUSINESS_ZONE);
    }
}
