package org.mannerth.starxcar.driver.application;

import org.mannerth.starxcar.auth.domain.User;
import org.mannerth.starxcar.auth.infrastructure.UserMapper;
import org.mannerth.starxcar.car.domain.Car;
import org.mannerth.starxcar.car.infrastructure.CarMapper;
import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.driver.domain.Driver;
import org.mannerth.starxcar.driver.domain.DriverApprovalDecision;
import org.mannerth.starxcar.driver.infrastructure.DriverMapper;
import org.mannerth.starxcar.driver.response.AdminDriverPageResponse;
import org.mannerth.starxcar.driver.response.AdminDriverResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.function.Function;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;






@Service
public class AdminDriverServiceImpl implements AdminDriverService {
    private final DriverMapper driverMapper;
    private final UserMapper userMapper;
    private final CarMapper carMapper;

    public AdminDriverServiceImpl(DriverMapper driverMapper, UserMapper userMapper, CarMapper carMapper) {
        this.driverMapper = driverMapper;
        this.userMapper = userMapper;
        this.carMapper = carMapper;
    }

    @Override
    public AdminDriverPageResponse list(long page, long size, String keyword, String approvalStatus) {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        List<Long> matchingUserIds = matchingUserIds(normalizedKeyword);
        LambdaQueryWrapper<Driver> query = new LambdaQueryWrapper<Driver>()
                .eq(approvalStatus != null && !approvalStatus.isBlank(), Driver::getApprovalStatus, approvalStatus)
                .and(normalizedKeyword != null && !normalizedKeyword.isBlank(), wrapper -> {
                    wrapper.like(Driver::getPhoneNumber, normalizedKeyword)
                            .or().like(Driver::getLicenseNumber, normalizedKeyword)
                            .or().like(Driver::getIdCardNo, normalizedKeyword);
                    if (!matchingUserIds.isEmpty()) {
                        wrapper.or().in(Driver::getId, matchingUserIds);
                    }
                })
                .orderByDesc(Driver::getId);
        IPage<Driver> result = driverMapper.selectPage(new Page<>(page, size), query);
        return new AdminDriverPageResponse(result.getCurrent(), result.getSize(), result.getTotal(),
                assemble(result.getRecords()));
    }

    @Override
    @Transactional
    public AdminDriverResponse review(Long driverId, DriverApprovalDecision decision) {
        Driver driver = requireDriver(driverId);
        if (!"pending".equals(driver.getApprovalStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该司机已经完成审核");
        }
        User user = userMapper.selectById(driverId);
        if (user == null || user.getType() != UserType.driver) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "司机用户不存在");
        }
        if (!"active".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "账号状态不可用，不能审核司机");
        }
        driver.setApprovalStatus(decision.name());
        driver.setWorkStatus("offline");
        driverMapper.updateById(driver);
        return assemble(driver, user, driver.getCarId() == null ? null : carMapper.selectById(driver.getCarId()));
    }

    private List<Long> matchingUserIds(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                        .eq(User::getType, UserType.driver)
                        .and(wrapper -> wrapper.like(User::getUsername, keyword)
                                .or().like(User::getRealName, keyword)
                                .or().like(User::getEmail, keyword)))
                .stream().map(User::getId).toList();
    }

    private List<AdminDriverResponse> assemble(List<Driver> drivers) {
        if (drivers.isEmpty()) {
            return List.of();
        }
        List<Long> driverIds = drivers.stream().map(Driver::getId).toList();
        Map<Long, User> users = userMapper.selectBatchIds(driverIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<Long> carIds = drivers.stream().map(Driver::getCarId).filter(id -> id != null).distinct().toList();
        Map<Long, Car> cars = carIds.isEmpty() ? Map.of() : carMapper.selectBatchIds(carIds).stream()
                .collect(Collectors.toMap(Car::getId, Function.identity()));
        return drivers.stream().map(driver -> assemble(driver, users.get(driver.getId()),
                        driver.getCarId() == null ? null : cars.get(driver.getCarId())))
                .toList();
    }

    private AdminDriverResponse assemble(Driver driver, User user, Car car) {
        return new AdminDriverResponse(driver.getId(), user == null ? null : user.getUsername(),
                user == null ? null : user.getRealName(), user == null ? null : user.getStatus(),
                driver.getPhoneNumber(), driver.getDisplayPhone(), driver.getLicenseNumber(), driver.getIdCardNo(),
                driver.getLicenseExpireDate(), driver.getRating(), driver.getApprovalStatus(), driver.getWorkStatus(),
                driver.getCarId(), car == null ? null : car.getCarNumber(), car == null ? null : car.getBrand(),
                car == null ? null : car.getColor(), user == null ? null : user.getCreateTime());
    }

    private Driver requireDriver(Long driverId) {
        Driver driver = driverMapper.selectById(driverId);
        if (driver == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "司机不存在");
        }
        return driver;
    }
}