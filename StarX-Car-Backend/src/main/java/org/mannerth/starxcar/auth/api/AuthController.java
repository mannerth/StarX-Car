package org.mannerth.starxcar.auth.api;

import org.mannerth.starxcar.auth.application.AuthService;
import org.mannerth.starxcar.auth.request.DriverRegistrationRequest;
import org.mannerth.starxcar.auth.request.LoginRequest;
import org.mannerth.starxcar.auth.request.PassengerRegistrationRequest;
import org.mannerth.starxcar.auth.request.RefreshTokenRequest;
import org.mannerth.starxcar.auth.response.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;






@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证", description = "登录、注册与令牌刷新接口")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "账号登录", description = "校验用户名和 BCrypt 密码，成功后返回 accessToken 和 refreshToken。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "登录成功"), @ApiResponse(responseCode = "400", description = "请求参数无效"), @ApiResponse(responseCode = "401", description = "用户名或密码错误")})
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新令牌", description = "使用未过期的 refreshToken 换取一组新的 accessToken 和 refreshToken。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "刷新成功"), @ApiResponse(responseCode = "400", description = "请求参数无效"), @ApiResponse(responseCode = "401", description = "刷新令牌无效、已过期或账号不可用")})
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/passengers/register")
    @Operation(summary = "乘客注册", description = "创建乘客账号及乘客资料；用户名和邮箱必须唯一，成功后直接返回令牌。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "注册成功"), @ApiResponse(responseCode = "400", description = "请求参数无效"), @ApiResponse(responseCode = "409", description = "用户名或邮箱已存在")})
    public TokenResponse registerPassenger(@Valid @RequestBody PassengerRegistrationRequest request) {
        return authService.registerPassenger(request);
    }

    @PostMapping("/drivers/register")
    @Operation(summary = "司机注册", description = "创建司机账号及驾驶证资料；用户名和邮箱必须唯一，成功后直接返回令牌。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "注册成功"), @ApiResponse(responseCode = "400", description = "请求参数无效"), @ApiResponse(responseCode = "409", description = "用户名或邮箱已存在")})
    public TokenResponse registerDriver(@Valid @RequestBody DriverRegistrationRequest request) {
        return authService.registerDriver(request);
    }
}