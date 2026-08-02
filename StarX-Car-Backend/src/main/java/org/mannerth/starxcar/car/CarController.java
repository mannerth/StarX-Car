package org.mannerth.starxcar.car;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.mannerth.starxcar.driver.Driver;
import org.mannerth.starxcar.driver.DriverCarBinding;
import org.mannerth.starxcar.driver.DriverCarBindingMapper;
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

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/cars")
@PreAuthorize("hasRole('DRIVER')")
@Tag(name = "车辆管理", description = "司机管理 driver.car_id 指向的当前用车")
@SecurityRequirement(name = "bearerAuth")
public class CarController {
    private final CarMapper carMapper;
    private final DriverMapper driverMapper;
    private final DriverCarBindingMapper driverCarBindingMapper;

    public CarController(CarMapper carMapper, DriverMapper driverMapper, DriverCarBindingMapper driverCarBindingMapper) {
        this.carMapper = carMapper;
        this.driverMapper = driverMapper;
        this.driverCarBindingMapper = driverCarBindingMapper;
    }

    @GetMapping("/me")
    @Operation(summary = "获取我的绑定车辆", description = "仅限 driver 角色；未绑定车辆时返回 404。")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或令牌无效"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非司机角色"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "司机、车辆或当前绑定不存在")})
    public Car getMyCar(@AuthenticationPrincipal String principal) {
        Driver driver = requireDriver(CurrentUser.id(principal));
        if (driver.getCarId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "尚未绑定车辆");
        }
        return requireCar(driver.getCarId());
    }

    @PostMapping
    @Operation(summary = "创建并绑定车辆", description = "仅限 driver 角色；创建车辆后会立即绑定至当前司机并成为当前用车，既有绑定车辆继续保留，车牌号必须唯一。")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "创建并绑定成功"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "车辆资料无效"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或令牌无效"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非司机角色"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "司机资料不存在"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "车牌号已存在或车辆已绑定其他司机")})
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
        activateBinding(driver, car.getId());
        return car;
    }

    @GetMapping("/{carId}")
    @Operation(summary = "获取绑定车辆详情", description = "仅限 driver 角色，且 carId 必须是当前司机绑定的车辆。")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或令牌无效"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "车辆未绑定到当前司机"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "司机或车辆不存在")})
    public Car get(@AuthenticationPrincipal String principal, @PathVariable Long carId) {
        requireBoundCar(CurrentUser.id(principal), carId);
        return requireCar(carId);
    }

    @PutMapping("/{carId}")
    @Operation(summary = "更新绑定车辆", description = "仅限 driver 角色，且 carId 必须是当前司机绑定的车辆；拆分车牌组合必须唯一。")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "更新成功"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "车辆资料无效"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或令牌无效"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "车辆未绑定到当前司机"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "司机或车辆不存在"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "车牌号已存在")})
    public Car update(@AuthenticationPrincipal String principal, @PathVariable Long carId,
                      @Valid @RequestBody CarRequest request) {
        Car car = requireBoundCar(CurrentUser.id(principal), carId);
        apply(car, request);
        try {
            carMapper.updateById(car);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "车牌号已存在", exception);
        }
        return requireCar(carId);
    }

    @DeleteMapping("/{carId}")
    @Operation(summary = "停用绑定车辆", description = "仅限 driver 角色，先解绑再将车辆标记为停用，以保留司机与车辆绑定历史。")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "停用成功"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或令牌无效"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "车辆未绑定到当前司机"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "司机或车辆不存在")})
    @Transactional
    public ApiResponse<Void> delete(@AuthenticationPrincipal String principal, @PathVariable Long carId) {
        Long driverId = CurrentUser.id(principal);
        requireBoundCar(driverId, carId);
        Driver driver = requireDriver(driverId);
        deactivateBinding(driverId, carId);
        driver.setCarId(null);
        driverMapper.updateById(driver);
        Car car = requireCar(carId);
        car.setVehicleStatus("disabled");
        carMapper.updateById(car);
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
        apply(car, request);
        car.setVehicleStatus("active");
        return car;
    }

    private void apply(Car car, CarRequest request) {
        car.setLicenseNumber(request.licenseNumber());
        car.setCarNumber(request.plateProvince() + request.plateLetter().toUpperCase() + request.plateSerial().toUpperCase());
        car.setCarType(request.carType());
        car.setBrand(request.brand());
        car.setColor(request.color());
        car.setPlateProvince(request.plateProvince());
        car.setPlateLetter(request.plateLetter().toUpperCase());
        car.setPlateSerial(request.plateSerial().toUpperCase());
        car.setRegistrationPlace(request.registrationPlace());
        car.setSeatCount(request.seatCount());
        car.setVehicleYear(request.vehicleYear());
    }

    private void activateBinding(Driver driver, Long carId) {
        DriverCarBinding carBinding = driverCarBindingMapper.selectOne(Wrappers.<DriverCarBinding>lambdaQuery()
                .eq(DriverCarBinding::getCarId, carId)
                .eq(DriverCarBinding::getStatus, "active"));
        if (carBinding != null && !carBinding.getDriverId().equals(driver.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "车辆已绑定其他司机");
        }
        if (carBinding == null) {
            DriverCarBinding binding = new DriverCarBinding();
            binding.setDriverId(driver.getId());
            binding.setCarId(carId);
            binding.setStatus("active");
            driverCarBindingMapper.insert(binding);
        }
        driver.setCarId(carId);
        driverMapper.updateById(driver);
    }

    private void deactivateBinding(Long driverId, Long carId) {
        driverCarBindingMapper.update(null, Wrappers.<DriverCarBinding>update()
                .eq("driver_id", driverId)
                .eq("car_id", carId)
                .eq("status", "active")
                .set("status", "inactive")
                .set("unbind_time", LocalDateTime.now()));
    }
}
