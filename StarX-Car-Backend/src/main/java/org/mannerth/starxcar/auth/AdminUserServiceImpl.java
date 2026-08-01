package org.mannerth.starxcar.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.mannerth.starxcar.auth.request.AdminUserUpdateRequest;
import org.mannerth.starxcar.auth.response.AdminUserPageResponse;
import org.mannerth.starxcar.auth.response.UserResponse;
import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.config.AdminProperties;
import org.mannerth.starxcar.driver.DriverMapper;
import org.mannerth.starxcar.driver.DriverCarBinding;
import org.mannerth.starxcar.driver.DriverCarBindingMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.mannerth.starxcar.passenger.PassengerMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private final UserMapper userMapper;
    private final PassengerMapper passengerMapper;
    private final DriverMapper driverMapper;
    private final DriverCarBindingMapper driverCarBindingMapper;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    public AdminUserServiceImpl(UserMapper userMapper, PassengerMapper passengerMapper, DriverMapper driverMapper,
                                DriverCarBindingMapper driverCarBindingMapper,
                                PasswordEncoder passwordEncoder, AdminProperties adminProperties) {
        this.userMapper = userMapper;
        this.passengerMapper = passengerMapper;
        this.driverMapper = driverMapper;
        this.driverCarBindingMapper = driverCarBindingMapper;
        this.passwordEncoder = passwordEncoder;
        this.adminProperties = adminProperties;
    }

    @Override
    public AdminUserPageResponse list(long page, long size, String keyword, UserType type) {
        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<User>()
                .eq(type != null, User::getType, type)
                .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper
                        .like(User::getUsername, keyword.trim())
                        .or()
                        .like(User::getEmail, keyword.trim()))
                .orderByDesc(User::getCreateTime)
                .orderByDesc(User::getId);
        IPage<User> result = userMapper.selectPage(new Page<>(page, size), query);
        List<UserResponse> records = result.getRecords().stream().map(UserResponse::from).toList();
        return new AdminUserPageResponse(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    @Override
    public UserResponse get(Long userId) {
        return UserResponse.from(requireUser(userId));
    }

    @Override
    public UserResponse update(Long userId, AdminUserUpdateRequest request) {
        User user = requireUser(userId);
        if (userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getUsername, request.username()).ne(User::getId, userId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        if (request.email() != null && userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getEmail, request.email()).ne(User::getId, userId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "邮箱已存在");
        }
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setGender(request.gender());
        user.setRealName(request.realName());
        user.setAvatarUrl(request.avatarUrl());
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        userMapper.updateById(user);
        return UserResponse.from(requireUser(userId));
    }

    @Override
    public void resetPassword(Long userId) {
        User user = requireUser(userId);
        user.setPassword(passwordEncoder.encode(adminProperties.getDefaultPassword()));
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long operatorId) {
        User user = requireUser(userId);
        if (userId.equals(operatorId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能删除当前登录的管理员账号");
        }
        if (user.getType() == UserType.admin) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "管理员账号不能通过用户管理删除");
        }
        if (user.getType() == UserType.passenger) {
            passengerMapper.deleteById(userId);
        } else if (user.getType() == UserType.driver) {
            driverCarBindingMapper.delete(Wrappers.<DriverCarBinding>lambdaQuery()
                    .eq(DriverCarBinding::getDriverId, userId));
            driverMapper.deleteById(userId);
        }
        userMapper.deleteById(userId);
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return user;
    }
}
