package org.mannerth.starxcar.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.mannerth.starxcar.auth.User;
import org.mannerth.starxcar.auth.UserMapper;
import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.order.request.OrderCancelRequest;
import org.mannerth.starxcar.order.request.StartServiceRequest;
import org.mannerth.starxcar.order.response.OrderStatusTransitionResponse;
import org.mannerth.starxcar.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Tag(name = "订单行程状态", description = "订单取消、司机到达与开始服务的受控状态转换")
@SecurityRequirement(name = "bearerAuth")
public class OrderLifecycleController {
    private final OrderLifecycleService lifecycleService;
    private final UserMapper userMapper;

    public OrderLifecycleController(OrderLifecycleService lifecycleService, UserMapper userMapper) {
        this.lifecycleService = lifecycleService;
        this.userMapper = userMapper;
    }

    @PostMapping("/api/orders/{orderNo}/cancel")
    @PreAuthorize("hasAnyRole('PASSENGER', 'DRIVER')")
    @Operation(summary = "取消订单", description = "仅限订单乘客或已接单司机。乘客只能取消 PENDING 或 ACCEPTED，司机只能取消自己已接的 ACCEPTED；到达、服务中和终态订单均不可取消。已接订单取消后释放司机和位置关联。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "取消成功"), @ApiResponse(responseCode = "400", description = "取消原因格式无效"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "非乘客/司机角色或账号不可用"), @ApiResponse(responseCode = "404", description = "订单不存在或不属于当前用户"), @ApiResponse(responseCode = "409", description = "订单状态或司机工作状态已变化，当前不可取消")})
    public OrderStatusTransitionResponse cancelOrder(@AuthenticationPrincipal String principal, @PathVariable String orderNo,
                                                      @Valid @RequestBody(required = false) OrderCancelRequest request) {
        Long userId = CurrentUser.id(principal);
        User user = requireActiveUser(userId);
        String reason = request == null ? null : request.reason();
        return switch (user.getType()) {
            case passenger -> lifecycleService.cancelByPassenger(userId, orderNo, reason);
            case driver -> lifecycleService.cancelByDriver(userId, orderNo, reason);
            case admin -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理员不能取消订单");
        };
    }

    @PostMapping("/api/orders/{orderNo}/arrive")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "确认到达上车点", description = "仅限已接此订单的账号有效 driver；订单必须处于 ACCEPTED。成功后写入服务端到达时间和 ARRIVE 审计事件。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "到达确认成功"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "非司机角色或账号不可用"), @ApiResponse(responseCode = "404", description = "订单不存在或不属于当前司机"), @ApiResponse(responseCode = "409", description = "订单状态已变化，不能确认到达")})
    public OrderStatusTransitionResponse arrive(@AuthenticationPrincipal String principal, @PathVariable String orderNo) {
        Long userId = CurrentUser.id(principal);
        requireDriver(userId);
        return lifecycleService.markDriverArrived(userId, orderNo);
    }

    @PostMapping("/api/orders/{orderNo}/start-service")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "开始服务", description = "仅限已接此订单的账号有效 driver；订单必须处于 DRIVER_ARRIVED。请求体需携带乘客手机尾号（后四位），服务端与乘客资料核对通过后写入开始时间、以服务端时间计算等待秒数和 START_SERVICE 审计事件；尾号缺失、格式错误或与乘客不一致时返回 400。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "开始服务成功"), @ApiResponse(responseCode = "400", description = "乘客手机尾号缺失、格式错误或校验失败"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "非司机角色或账号不可用"), @ApiResponse(responseCode = "404", description = "订单不存在或不属于当前司机"), @ApiResponse(responseCode = "409", description = "订单状态或到达时间异常，不能开始服务")})
    public OrderStatusTransitionResponse startService(@AuthenticationPrincipal String principal,
                                                       @PathVariable String orderNo,
                                                       @Valid @RequestBody(required = false) StartServiceRequest request) {
        Long userId = CurrentUser.id(principal);
        requireDriver(userId);
        return lifecycleService.startService(userId, orderNo, request == null ? null : request.phoneTail());
    }

    private User requireActiveUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !"active".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已停用或注销");
        }
        return user;
    }

    private void requireDriver(Long userId) {
        if (requireActiveUser(userId).getType() != UserType.driver) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不是司机");
        }
    }
}

