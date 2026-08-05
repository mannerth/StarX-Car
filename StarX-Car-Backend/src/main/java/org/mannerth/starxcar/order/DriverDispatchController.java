package org.mannerth.starxcar.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.mannerth.starxcar.order.response.OrderAcceptedResponse;
import org.mannerth.starxcar.order.response.OrderPageResponse;
import org.mannerth.starxcar.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@PreAuthorize("hasRole('DRIVER')")
@Tag(name = "司机抢单", description = "司机查看可抢即时订单并进行并发安全的抢单")
@SecurityRequirement(name = "bearerAuth")
public class DriverDispatchController {
    private final DriverDispatchService dispatchService;

    public DriverDispatchController(DriverDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @GetMapping("/api/drivers/me/orders/pending")
    @Operation(summary = "分页查询待抢订单", description = "仅限处于 online、审核通过、账号有效、当前用车有效且位置新鲜的 driver；按创建时间倒序返回尚未被接单的 PENDING 即时订单。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "查询成功"), @ApiResponse(responseCode = "400", description = "分页参数无效"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "非司机角色或账号不可用"), @ApiResponse(responseCode = "404", description = "司机资料不存在"), @ApiResponse(responseCode = "409", description = "司机未处于可听单状态、当前车辆或最新位置不可用")})
    public OrderPageResponse pendingOrders(@AuthenticationPrincipal String principal,
                                           @RequestParam(defaultValue = "1") @Min(1) long page,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size) {
        return dispatchService.listPendingOrders(CurrentUser.id(principal), page, size);
    }

    @PostMapping("/api/orders/{orderNo}/accept")
    @Operation(summary = "抢单", description = "仅限满足听单资格的 driver；使用订单 ID、PENDING 状态、空 driver_id 和 version 进行原子条件更新。成功后写 ACCEPT 审计事件、司机变为 busy，并关联其新鲜最新位置。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "抢单成功"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "非司机角色或账号不可用"), @ApiResponse(responseCode = "404", description = "司机资料或订单不存在"), @ApiResponse(responseCode = "409", description = "订单已被抢、司机不满足听单资格或位置不可用")})
    public OrderAcceptedResponse acceptOrder(@AuthenticationPrincipal String principal, @PathVariable String orderNo) {
        return dispatchService.acceptOrder(CurrentUser.id(principal), orderNo);
    }
}
