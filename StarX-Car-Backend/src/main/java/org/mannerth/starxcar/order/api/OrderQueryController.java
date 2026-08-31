package org.mannerth.starxcar.order.api;

import org.mannerth.starxcar.auth.domain.User;
import org.mannerth.starxcar.auth.infrastructure.UserMapper;
import org.mannerth.starxcar.order.application.OrderQueryService;
import org.mannerth.starxcar.order.domain.OrderStatus;
import org.mannerth.starxcar.order.response.OrderDetailResponse;
import org.mannerth.starxcar.order.response.OrderPageResponse;
import org.mannerth.starxcar.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;






@RestController
@Validated
@Tag(name = "订单查询", description = "乘客、司机和管理员按各自权限读取订单")
@SecurityRequirement(name = "bearerAuth")
public class OrderQueryController {
    private final OrderQueryService orderQueryService;
    private final UserMapper userMapper;

    public OrderQueryController(OrderQueryService orderQueryService, UserMapper userMapper) {
        this.orderQueryService = orderQueryService;
        this.userMapper = userMapper;
    }

    @GetMapping("/api/passengers/me/orders")
    @PreAuthorize("hasRole('PASSENGER')")
    @Operation(summary = "分页查询我的订单", description = "仅限 passenger；按创建时间倒序返回当前乘客订单，可按状态筛选。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "查询成功"), @ApiResponse(responseCode = "400", description = "分页或状态参数无效"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "非乘客角色")})
    public OrderPageResponse passengerOrders(@AuthenticationPrincipal String principal,
                                             @RequestParam(defaultValue = "1") @Min(1) long page,
                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
                                             @RequestParam(required = false) OrderStatus status) {
        return orderQueryService.listPassengerOrders(CurrentUser.id(principal), page, size, status);
    }

    @GetMapping("/api/drivers/me/orders")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "分页查询我的已接订单", description = "仅限 driver；只返回 driver_id 为当前司机的订单，可按状态筛选。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "查询成功"), @ApiResponse(responseCode = "400", description = "分页或状态参数无效"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "非司机角色")})
    public OrderPageResponse driverOrders(@AuthenticationPrincipal String principal,
                                          @RequestParam(defaultValue = "1") @Min(1) long page,
                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
                                          @RequestParam(required = false) OrderStatus status) {
        return orderQueryService.listDriverOrders(CurrentUser.id(principal), page, size, status);
    }

    @GetMapping("/api/orders/{orderNo}")
    @Operation(summary = "获取订单详情", description = "乘客只能读取本人订单，司机只能读取自己已接订单，管理员可读取任意订单。无归属权限时返回 404，避免泄露订单存在性。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "查询成功"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "账号不可用"), @ApiResponse(responseCode = "404", description = "订单不存在或无读取权限")})
    public OrderDetailResponse detail(@AuthenticationPrincipal String principal, @PathVariable String orderNo) {
        Long userId = CurrentUser.id(principal);
        User user = userMapper.selectById(userId);
        if (user == null || !"active".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已停用或注销");
        }
        return orderQueryService.getOrder(userId, user.getType(), orderNo);
    }
}