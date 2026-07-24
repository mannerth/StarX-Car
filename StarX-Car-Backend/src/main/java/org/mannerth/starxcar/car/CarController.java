package org.mannerth.starxcar.car;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.mannerth.starxcar.driver.Driver;
import org.mannerth.starxcar.driver.DriverMapper;
import org.mannerth.starxcar.security.CurrentUser;
import org.mannerth.starxcar.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/cars")
@PreAuthorize("hasRole('DRIVER')")
@Tag(name = "车辆管理", description = "司机管理当前绑定车辆")
@SecurityRequirement(name = "bearerAuth")
public class CarController {
    private final CarMapper carMapper;
    private final DriverMapper driverMapper;

    public CarController(CarMapper carMapper, DriverMapper driverMapper) {
        this.carMapper = carMapper;
        this.driverMapper = driverMapper;
    }

    @GetMapping("/me")
    @Operation(summary = "获取我的绑定车辆", description = "仅限 driver 角色；未绑定车辆时返回 404。")
    public Car getMyCar(@AuthenticationPrincipal String principal) {
        Driver driver = requireDriver(CurrentUser.id(principal));
        if (driver.getCarId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "尚未绑定车辆");
        }
        return requireCar(driver.getCarId());
    }

    @PostMapping
    @Operation(summary = "创建并绑定车辆", description = "仅限 driver 角色；创建车辆后会立即绑定至当前司机，车牌号必须唯一。")
    @Transactional
    public Car create(@AuthenticationPrincipal String principal, @Valid @RequestBody CarRequest request) {
        Long driverId = CurrentUser.id(principal);
        Driver driver = requireDriver(driverId);
        Car car = from(request);
        try {
            carMapper.insert(car);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "车牌号已存在", exception);
        }
        driver.setCarId(car.getId());
        driverMapper.updateById(driver);
        return car;
    }

    @GetMapping("/{carId}")
    @Operation(summary = "获取绑定车辆详情", description = "仅限 driver 角色，且 carId 必须是当前司机绑定的车辆。")
    public Car get(@AuthenticationPrincipal String principal, @PathVariable Long carId) {
        requireBoundCar(CurrentUser.id(principal), carId);
        return requireCar(carId);
    }

    @PutMapping("/{carId}")
    @Operation(summary = "更新绑定车辆", description = "仅限 driver 角色，且 carId 必须是当前司机绑定的车辆；车牌号必须唯一。")
    public Car update(@AuthenticationPrincipal String principal, @PathVariable Long carId,
                      @Valid @RequestBody CarRequest request) {
        Car car = requireBoundCar(CurrentUser.id(principal), carId);
        car.setLicenseNumber(request.licenseNumber());
        car.setCarNumber(request.carNumber());
        car.setCarType(request.carType());
        car.setBrand(request.brand());
        car.setColor(request.color());
        try {
            carMapper.updateById(car);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "车牌号已存在", exception);
        }
        return requireCar(carId);
    }

    @DeleteMapping("/{carId}")
    @Operation(summary = "删除绑定车辆", description = "仅限 driver 角色，先解绑再删除车辆记录；此操作不可恢复。")
    @Transactional
    public ApiResponse<Void> delete(@AuthenticationPrincipal String principal, @PathVariable Long carId) {
        Long driverId = CurrentUser.id(principal);
        requireBoundCar(driverId, carId);
        Driver driver = requireDriver(driverId);
        driver.setCarId(null);
        driverMapper.updateById(driver);
        carMapper.deleteById(carId);
        return ApiResponse.success(null);
    }

    private Car requireBoundCar(Long driverId, Long carId) {
        Driver driver = requireDriver(driverId);
        if (!carId.equals(driver.getCarId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该车辆未绑定到当前司机");
        }
        return requireCar(carId);
    }

    private Driver requireDriver(Long userId) {
        Driver driver = driverMapper.selectById(userId);
        if (driver == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "司机不存在");
        }
        return driver;
    }

    private Car requireCar(Long carId) {
        Car car = carMapper.selectById(carId);
        if (car == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "车辆不存在");
        }
        return car;
    }

    private Car from(CarRequest request) {
        Car car = new Car();
        car.setLicenseNumber(request.licenseNumber());
        car.setCarNumber(request.carNumber());
        car.setCarType(request.carType());
        car.setBrand(request.brand());
        car.setColor(request.color());
        return car;
    }
}
