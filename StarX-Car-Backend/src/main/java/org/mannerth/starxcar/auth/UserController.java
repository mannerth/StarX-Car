package org.mannerth.starxcar.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.mannerth.starxcar.auth.request.UserProfileUpdateRequest;
import org.mannerth.starxcar.auth.response.UserResponse;
import org.mannerth.starxcar.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users/me")
@Tag(name = "用户资料", description = "当前登录用户的基础资料")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserMapper userMapper;

    public UserController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping
    @Operation(summary = "获取我的用户资料", description = "返回当前 accessToken 对应用户的基础资料，不包含密码。")
    public UserResponse get(@AuthenticationPrincipal String principal) {
        return UserResponse.from(requireUser(CurrentUser.id(principal)));
    }

    @PutMapping
    @Operation(summary = "更新我的用户资料", description = "更新用户名、邮箱和性别；用户名和非空邮箱必须保持唯一。")
    public UserResponse update(@AuthenticationPrincipal String principal, @Valid @RequestBody UserProfileUpdateRequest request) {
        Long userId = CurrentUser.id(principal);
        User user = requireUser(userId);
        if (userMapper.exists(Wrappers.<User>lambdaQuery().eq(User::getUsername, request.username()).ne(User::getId, userId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        if (request.email() != null && userMapper.exists(Wrappers.<User>lambdaQuery().eq(User::getEmail, request.email()).ne(User::getId, userId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "邮箱已存在");
        }
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setGender(request.gender());
        userMapper.updateById(user);
        return UserResponse.from(requireUser(userId));
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return user;
    }
}
