package org.mannerth.starxcar.driver;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.mannerth.starxcar.auth.UserMapper;
import org.mannerth.starxcar.car.Car;
import org.mannerth.starxcar.car.CarMapper;
import org.mannerth.starxcar.security.CurrentUser;
import org.mannerth.starxcar.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/drivers/me")
@PreAuthorize("hasRole('DRIVER')")
@Tag(name = "司机资料", description = "仅限当前司机操作自己的资料和车辆绑定")
@SecurityRequirement(name = "bearerAuth")
public class DriverController {
    private final DriverMapper driverMapper;
    private final UserMapper userMapper;
    private final CarMapper carMapper;
    private final DriverCarBindingMapper driverCarBindingMapper;

    public DriverController(DriverMapper driverMapper, UserMapper userMapper, CarMapper carMapper,
                            DriverCarBindingMapper driverCarBindingMapper) {
        this.driverMapper = driverMapper;
        this.userMapper = userMapper;
        this.carMapper = carMapper;
        this.driverCarBindingMapper = driverCarBindingMapper;
    }

    @GetMapping
    @Operation(summary = "获取我的司机资料", description = "仅限 driver 角色，返回驾驶证、评分和当前绑定车辆。")
    public Driver get(@AuthenticationPrincipal String principal) {
        return requireDriver(CurrentUser.id(principal));
    }

    @PutMapping
    @Operation(summary = "更新我的司机资料", description = "仅限 driver 角色，可更新手机号、驾驶证号、身份证号和驾驶证有效期。")
    public Driver update(@AuthenticationPrincipal String principal, @Valid @RequestBody DriverUpdateRequest request) {
        Driver driver = requireDriver(CurrentUser.id(principal));
        driver.setPhoneNumber(request.phoneNumber());
        driver.setLicenseNumber(request.licenseNumber());
        driver.setIdCardNo(request.idCardNo());
        driver.setLicenseExpireDate(request.licenseExpireDate());
        driverMapper.updateById(driver);
        return requireDriver(driver.getId());
    }

    @PutMapping("/car")
    @Operation(summary = "绑定车辆", description = "仅限 driver 角色，将可用车辆绑定到当前司机；一个司机和一辆车同时只能有一个当前绑定。")
    @Transactional
    public Driver bindCar(@AuthenticationPrincipal String principal, @Valid @RequestBody CarBindingRequest request) {
        Car car = carMapper.selectById(request.carId());
        if (car == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "车辆不存在");
        }
        if (!"active".equals(car.getVehicleStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "车辆当前不可绑定");
        }
        Driver driver = requireDriver(CurrentUser.id(principal));
        DriverCarBinding binding = driverCarBindingMapper.selectOne(Wrappers.<DriverCarBinding>lambdaQuery()
                .eq(DriverCarBinding::getCarId, request.carId())
                .eq(DriverCarBinding::getStatus, "active"));
        if (binding != null && !binding.getDriverId().equals(driver.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "车辆已绑定其他司机");
        }
        deactivateBinding(driver.getId());
        DriverCarBinding newBinding = new DriverCarBinding();
        newBinding.setDriverId(driver.getId());
        newBinding.setCarId(request.carId());
        newBinding.setStatus("active");
        driverCarBindingMapper.insert(newBinding);
        driver.setCarId(request.carId());
        driverMapper.updateById(driver);
        return requireDriver(driver.getId());
    }

    @DeleteMapping("/car")
    @Operation(summary = "解绑车辆", description = "仅限 driver 角色，结束当前绑定并保留绑定历史，不会删除车辆记录。")
    @Transactional
    public ApiResponse<Void> unbindCar(@AuthenticationPrincipal String principal) {
        Driver driver = requireDriver(CurrentUser.id(principal));
        deactivateBinding(driver.getId());
        driver.setCarId(null);
        driverMapper.updateById(driver);
        return ApiResponse.success(null);
    }

    @DeleteMapping
    @Operation(summary = "注销司机账号", description = "删除当前司机资料及对应用户账号，此操作不可恢复。")
    @Transactional
    public ApiResponse<Void> delete(@AuthenticationPrincipal String principal) {
        Long userId = CurrentUser.id(principal);
        driverCarBindingMapper.delete(Wrappers.<DriverCarBinding>lambdaQuery()
                .eq(DriverCarBinding::getDriverId, userId));
        driverMapper.deleteById(userId);
        userMapper.deleteById(userId);
        return ApiResponse.success(null);
    }

    private Driver requireDriver(Long userId) {
        Driver driver = driverMapper.selectById(userId);
        if (driver == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "司机不存在");
        }
        return driver;
    }

    private void deactivateBinding(Long driverId) {
        driverCarBindingMapper.update(null, Wrappers.<DriverCarBinding>lambdaUpdate()
                .eq(DriverCarBinding::getDriverId, driverId)
                .eq(DriverCarBinding::getStatus, "active")
                .set(DriverCarBinding::getStatus, "inactive")
                .set(DriverCarBinding::getUnbindTime, LocalDateTime.now()));
    }
}
