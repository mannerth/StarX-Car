package org.mannerth.starxcar.driver;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.mannerth.starxcar.auth.UserMapper;
import org.mannerth.starxcar.auth.User;
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
import java.util.List;

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
    private final DriverWorkService driverWorkService;

    public DriverController(DriverMapper driverMapper, UserMapper userMapper, CarMapper carMapper,
                            DriverCarBindingMapper driverCarBindingMapper, DriverWorkService driverWorkService) {
        this.driverMapper = driverMapper;
        this.userMapper = userMapper;
        this.carMapper = carMapper;
        this.driverCarBindingMapper = driverCarBindingMapper;
        this.driverWorkService = driverWorkService;
    }

    @GetMapping
    @Operation(summary = "获取我的司机资料", description = "仅限 driver 角色，返回驾驶证、评分和 driver.car_id 指向的当前用车。全部有效绑定车辆请调用 /cars。")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或令牌无效"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非司机角色"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "司机资料不存在")})
    public Driver get(@AuthenticationPrincipal String principal) {
        return requireDriver(CurrentUser.id(principal));
    }

    @GetMapping("/cars")
    @Operation(summary = "获取我的全部绑定车辆", description = "仅限 driver 角色；返回当前仍有效绑定到本人的全部车辆。driver.car_id 指向其中当前实际使用的车辆；已解绑或停用的历史绑定不会返回。")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或令牌无效"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非司机角色"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "司机资料不存在")})
    public List<Car> getCars(@AuthenticationPrincipal String principal) {
        Long driverId = CurrentUser.id(principal);
        requireDriver(driverId);
        List<Long> carIds = driverCarBindingMapper.selectList(Wrappers.<DriverCarBinding>lambdaQuery()
                        .eq(DriverCarBinding::getDriverId, driverId)
                        .eq(DriverCarBinding::getStatus, "active")
                        .orderByDesc(DriverCarBinding::getBindTime))
                .stream()
                .map(DriverCarBinding::getCarId)
                .toList();
        return carIds.isEmpty() ? List.of() : carMapper.selectBatchIds(carIds);
    }

    @PutMapping
    @Operation(summary = "更新我的司机资料", description = "仅限 driver 角色，可更新手机号、驾驶证号、身份证号和驾驶证有效期。")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "更新成功"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "资料格式无效"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或令牌无效"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非司机角色"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "司机资料不存在")})
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
    @Operation(summary = "绑定车辆并设为当前用车", description = "仅限 driver 角色，将可用车辆绑定到当前司机并写入 driver.car_id 作为当前用车。一个司机可保留多辆有效绑定车辆；一辆车不能同时绑定其他司机。")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "绑定成功"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数无效"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或令牌无效"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非司机角色"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "车辆或司机不存在"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "车辆不可用或已绑定其他司机")})
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
        if (binding == null) {
            DriverCarBinding newBinding = new DriverCarBinding();
            newBinding.setDriverId(driver.getId());
            newBinding.setCarId(request.carId());
            newBinding.setStatus("active");
            driverCarBindingMapper.insert(newBinding);
        }
        driver.setCarId(request.carId());
        driverMapper.updateById(driver);
        return requireDriver(driver.getId());
    }

    @PutMapping("/current-car")
    @Operation(summary = "切换当前用车", description = "仅限 driver 角色；仅能从本人有效绑定且状态为 active 的车辆中选择，并将所选车辆 ID 写入 driver.car_id。该操作不改变车辆绑定历史。")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "切换成功"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数无效"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或令牌无效"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "车辆未有效绑定到当前司机"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "司机或车辆不存在"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "车辆当前不可使用")})
    @Transactional
    public Driver changeCurrentCar(@AuthenticationPrincipal String principal, @Valid @RequestBody CarBindingRequest request) {
        Driver driver = requireDriver(CurrentUser.id(principal));
        Car car = carMapper.selectById(request.carId());
        if (car == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "车辆不存在");
        }
        if (!"active".equals(car.getVehicleStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "车辆当前不可使用");
        }
        boolean bound = driverCarBindingMapper.exists(Wrappers.<DriverCarBinding>lambdaQuery()
                .eq(DriverCarBinding::getDriverId, driver.getId())
                .eq(DriverCarBinding::getCarId, request.carId())
                .eq(DriverCarBinding::getStatus, "active"));
        if (!bound) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该车辆未有效绑定到当前司机");
        }
        driver.setCarId(request.carId());
        driverMapper.updateById(driver);
        return requireDriver(driver.getId());
    }

    @DeleteMapping("/car")
    @Operation(summary = "解绑当前用车", description = "仅限 driver 角色，解除 driver.car_id 指向的当前车辆绑定并保留其历史；不会影响该司机的其他有效绑定车辆。")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "解绑成功"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或令牌无效"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非司机角色"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "司机资料不存在")})
    @Transactional
    public ApiResponse<Void> unbindCar(@AuthenticationPrincipal String principal) {
        Driver driver = requireDriver(CurrentUser.id(principal));
        if (driver.getCarId() != null) {
            deactivateBinding(driver.getId(), driver.getCarId());
        }
        driver.setCarId(null);
        driverMapper.updateById(driver);
        return ApiResponse.success(null);
    }

    @DeleteMapping
    @Operation(summary = "注销司机账号", description = "仅限 driver 角色；将当前账号标记为 cancelled，解除当前用车并结束全部有效车辆绑定，保留司机资料和全部车辆绑定历史。存在进行中订单时拒绝注销，避免订单悬空；账号不可恢复登录。")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "注销成功"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录或令牌无效"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非司机角色"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "司机或用户资料不存在"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "存在进行中订单，暂不能注销")})
    @Transactional
    public ApiResponse<Void> delete(@AuthenticationPrincipal String principal) {
        Long userId = CurrentUser.id(principal);
        Driver driver = requireDriver(userId);
        driverWorkService.ensureNoOngoingOrders(driver.getId());
        deactivateBindings(driver.getId());
        driver.setCarId(null);
        driverMapper.updateById(driver);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        user.setStatus("cancelled");
        userMapper.updateById(user);
        return ApiResponse.success(null);
    }

    private Driver requireDriver(Long userId) {
        Driver driver = driverMapper.selectById(userId);
        if (driver == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "司机不存在");
        }
        return driver;
    }

    private void deactivateBinding(Long driverId, Long carId) {
        driverCarBindingMapper.update(null, Wrappers.<DriverCarBinding>update()
                .eq("driver_id", driverId)
                .eq("car_id", carId)
                .eq("status", "active")
                .set("status", "inactive")
                .set("unbind_time", LocalDateTime.now()));
    }

    private void deactivateBindings(Long driverId) {
        driverCarBindingMapper.update(null, Wrappers.<DriverCarBinding>update()
                .eq("driver_id", driverId)
                .eq("status", "active")
                .set("status", "inactive")
                .set("unbind_time", LocalDateTime.now()));
    }
}
