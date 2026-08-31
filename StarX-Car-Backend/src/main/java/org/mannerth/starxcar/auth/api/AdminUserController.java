package org.mannerth.starxcar.auth.api;

import org.mannerth.starxcar.auth.application.AdminUserService;
import org.mannerth.starxcar.auth.request.AdminUserUpdateRequest;
import org.mannerth.starxcar.auth.response.AdminUserPageResponse;
import org.mannerth.starxcar.auth.response.UserResponse;
import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;






@RestController
@Validated
@RequestMapping("/api/admin/users")
@Tag(name = "后台用户管理", description = "仅管理员可查询、编辑及删除乘客和司机用户")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @Operation(summary = "分页查询用户", description = "管理员按用户名或邮箱关键字、用户类型筛选用户；返回基础资料，不包含密码。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "查询成功"), @ApiResponse(responseCode = "401", description = "未登录或令牌无效"), @ApiResponse(responseCode = "403", description = "非管理员"), @ApiResponse(responseCode = "400", description = "分页参数无效")})
    public AdminUserPageResponse list(@RequestParam(defaultValue = "1") @Min(1) long page,
                                      @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) UserType type) {
        return adminUserService.list(page, size, keyword, type);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "查看用户详情", description = "管理员根据用户 ID 查看基础资料，不包含密码。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "查询成功"), @ApiResponse(responseCode = "401", description = "未登录或令牌无效"), @ApiResponse(responseCode = "403", description = "非管理员"), @ApiResponse(responseCode = "404", description = "用户不存在")})
    public UserResponse get(@PathVariable Long userId) {
        return adminUserService.get(userId);
    }

    @PutMapping("/{userId}")
    @Operation(summary = "编辑用户资料", description = "管理员编辑用户名、邮箱、性别、实名资料、头像和账号状态；用户名和非空邮箱必须唯一，用户类型及密码不在此接口修改。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "更新成功"), @ApiResponse(responseCode = "400", description = "参数无效"), @ApiResponse(responseCode = "401", description = "未登录或令牌无效"), @ApiResponse(responseCode = "403", description = "非管理员"), @ApiResponse(responseCode = "404", description = "用户不存在"), @ApiResponse(responseCode = "409", description = "用户名或邮箱已存在")})
    public UserResponse update(@PathVariable Long userId, @Valid @RequestBody AdminUserUpdateRequest request) {
        return adminUserService.update(userId, request);
    }

    @PostMapping("/{userId}/reset-password")
    @Operation(summary = "重置用户密码", description = "任一管理员可将任意用户（包括管理员）密码重置为服务端配置的默认密码；默认密码不会返回给客户端。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "重置成功"), @ApiResponse(responseCode = "401", description = "未登录或令牌无效"), @ApiResponse(responseCode = "403", description = "非管理员"), @ApiResponse(responseCode = "404", description = "用户不存在")})
    public void resetPassword(@PathVariable Long userId) {
        adminUserService.resetPassword(userId);
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "删除用户", description = "管理员删除乘客或司机账号，并在同一事务中删除对应扩展资料；不能删除管理员账号或当前登录账号。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "删除成功"), @ApiResponse(responseCode = "400", description = "禁止删除管理员或当前账号"), @ApiResponse(responseCode = "401", description = "未登录或令牌无效"), @ApiResponse(responseCode = "403", description = "非管理员"), @ApiResponse(responseCode = "404", description = "用户不存在")})
    public void delete(@PathVariable Long userId, @AuthenticationPrincipal String principal) {
        adminUserService.delete(userId, CurrentUser.id(principal));
    }
}