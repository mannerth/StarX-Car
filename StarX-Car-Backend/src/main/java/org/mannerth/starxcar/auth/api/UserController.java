package org.mannerth.starxcar.auth.api;

import org.mannerth.starxcar.auth.domain.User;
import org.mannerth.starxcar.auth.infrastructure.UserMapper;
import org.mannerth.starxcar.auth.request.PasswordChangeRequest;
import org.mannerth.starxcar.auth.request.UserProfileUpdateRequest;
import org.mannerth.starxcar.auth.response.UserResponse;
import org.mannerth.starxcar.infrastructure.image.ImageService;
import org.mannerth.starxcar.security.CurrentUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;






@RestController
@RequestMapping("/api/users/me")
@Tag(name = "用户资料", description = "当前登录用户的基础资料")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ImageService imageService;

    public UserController(UserMapper userMapper, PasswordEncoder passwordEncoder, ImageService imageService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.imageService = imageService;
    }

    @GetMapping
    @Operation(summary = "获取我的用户资料", description = "返回当前 accessToken 对应用户的基础资料，不包含密码。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "查询成功"), @ApiResponse(responseCode = "401", description = "未登录或令牌无效"), @ApiResponse(responseCode = "404", description = "用户不存在")})
    public UserResponse get(@AuthenticationPrincipal String principal) {
        return UserResponse.from(requireUser(CurrentUser.id(principal)));
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "更新我的用户资料", description = "更新用户名、邮箱、性别、真实姓名和头像；用户名和非空邮箱必须保持唯一。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "更新成功"), @ApiResponse(responseCode = "400", description = "资料格式无效"), @ApiResponse(responseCode = "401", description = "未登录或令牌无效"), @ApiResponse(responseCode = "404", description = "用户不存在"), @ApiResponse(responseCode = "409", description = "用户名或邮箱已存在")})
    public UserResponse update(@AuthenticationPrincipal String principal, @Valid @RequestBody UserProfileUpdateRequest request) {
        return update(principal, request, null, true);
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传头像并更新我的用户资料", description = "当前登录用户以 multipart/form-data 提交基础资料和可选 avatar 图片文件；后端上传图片到图床后，将返回的访问 URL 保存为头像。图床不可用时不会更新用户资料。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "资料和头像更新成功"), @ApiResponse(responseCode = "400", description = "资料或图片参数无效"), @ApiResponse(responseCode = "401", description = "未登录或令牌无效"), @ApiResponse(responseCode = "409", description = "用户名或邮箱已存在"), @ApiResponse(responseCode = "502", description = "图床服务异常"), @ApiResponse(responseCode = "503", description = "图床账号未配置")})
    public UserResponse updateWithAvatar(@AuthenticationPrincipal String principal,
                                         @Valid @ModelAttribute UserProfileUpdateRequest request,
                                         @RequestPart(value = "avatar", required = false) MultipartFile avatar) {
        return update(principal, request, avatar, false);
    }

    private UserResponse update(String principal, UserProfileUpdateRequest request, MultipartFile avatar,
                                boolean acceptAvatarUrl) {
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
        user.setRealName(request.realName());
        if (avatar != null) {
            user.setAvatarUrl(imageService.upload(avatar).url());
        } else if (acceptAvatarUrl) {
            user.setAvatarUrl(request.avatarUrl());
        }
        userMapper.updateById(user);
        return UserResponse.from(requireUser(userId));
    }

    @PostMapping("/password")
    @Operation(summary = "修改我的密码", description = "当前登录用户必须先验证原密码；验证成功后使用 BCrypt 保存新密码。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "密码修改成功"), @ApiResponse(responseCode = "400", description = "新密码参数无效"), @ApiResponse(responseCode = "401", description = "未登录、令牌无效或原密码错误"), @ApiResponse(responseCode = "404", description = "用户不存在")})
    public void changePassword(@AuthenticationPrincipal String principal, @Valid @RequestBody PasswordChangeRequest request) {
        User user = requireUser(CurrentUser.id(principal));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "原密码错误");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(user);
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return user;
    }
}