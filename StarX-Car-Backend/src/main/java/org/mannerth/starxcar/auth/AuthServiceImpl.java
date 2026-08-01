package org.mannerth.starxcar.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.mannerth.starxcar.auth.request.DriverRegistrationRequest;
import org.mannerth.starxcar.auth.request.LoginRequest;
import org.mannerth.starxcar.auth.request.PassengerRegistrationRequest;
import org.mannerth.starxcar.auth.request.RefreshTokenRequest;
import org.mannerth.starxcar.auth.response.TokenResponse;
import org.mannerth.starxcar.auth.response.UserResponse;
import org.mannerth.starxcar.config.JwtProperties;
import org.mannerth.starxcar.constant.GenderEnum;
import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.driver.Driver;
import org.mannerth.starxcar.driver.DriverMapper;
import org.mannerth.starxcar.passenger.Passenger;
import org.mannerth.starxcar.passenger.PassengerMapper;
import org.mannerth.starxcar.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PassengerMapper passengerMapper;
    private final DriverMapper driverMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthServiceImpl(UserMapper userMapper, PassengerMapper passengerMapper, DriverMapper driverMapper,
                           PasswordEncoder passwordEncoder, JwtService jwtService, JwtProperties jwtProperties) {
        this.userMapper = userMapper;
        this.passengerMapper = passengerMapper;
        this.driverMapper = driverMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        requireActive(user);
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);
        return issueTokens(user);
    }

    @Override
    public TokenResponse refresh(RefreshTokenRequest request) {
        var claims = jwtService.parseToken(request.refreshToken())
                .filter(value -> "refresh".equals(value.get("token_type", String.class)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "刷新令牌无效或已过期"));
        Long userId;
        try {
            userId = Long.valueOf(claims.getSubject());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "刷新令牌无效");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在");
        }
        requireActive(user);
        return issueTokens(user);
    }

    @Override
    @Transactional
    public TokenResponse registerPassenger(PassengerRegistrationRequest request) {
        User user = createUser(request.username(), request.password(), request.email(), request.gender(), request.realName(), request.avatarUrl(), UserType.passenger);
        Passenger passenger = new Passenger();
        passenger.setId(user.getId());
        passenger.setPhoneNumber(request.phoneNumber());
        passengerMapper.insert(passenger);
        return issueTokens(user);
    }

    @Override
    @Transactional
    public TokenResponse registerDriver(DriverRegistrationRequest request) {
        User user = createUser(request.username(), request.password(), request.email(), request.gender(), request.realName(), request.avatarUrl(), UserType.driver);
        Driver driver = new Driver();
        driver.setId(user.getId());
        driver.setPhoneNumber(request.phoneNumber());
        driver.setLicenseNumber(request.licenseNumber());
        driverMapper.insert(driver);
        return issueTokens(user);
    }

    private User createUser(String username, String password, String email, GenderEnum gender, String realName,
                            String avatarUrl, UserType type) {
        if (userMapper.exists(Wrappers.<User>lambdaQuery().eq(User::getUsername, username))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        if (email != null && userMapper.exists(Wrappers.<User>lambdaQuery().eq(User::getEmail, email))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "邮箱已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setGender(gender);
        user.setRealName(realName);
        user.setAvatarUrl(avatarUrl);
        user.setType(type);
        user.setStatus("active");
        userMapper.insert(user);
        return user;
    }

    private void requireActive(User user) {
        if (!"active".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已停用或注销");
        }
    }

    private TokenResponse issueTokens(User user) {
        Map<String, String> accessClaims = Map.of("token_type", "access", "role", user.getType().name());
        Map<String, String> refreshClaims = Map.of("token_type", "refresh", "role", user.getType().name());
        String subject = user.getId().toString();
        return new TokenResponse(
                jwtService.createToken(subject, accessClaims, jwtProperties.getExpire()),
                jwtService.createToken(subject, refreshClaims, jwtProperties.getRefreshExpire()),
                jwtProperties.getExpire(),
                UserResponse.from(user));
    }
}
