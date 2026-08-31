package org.mannerth.starxcar.order.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.mannerth.starxcar.auth.User;
import org.mannerth.starxcar.auth.UserMapper;
import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.order.OrderStatus;
import org.mannerth.starxcar.order.OrderType;
import org.mannerth.starxcar.order.admin.request.AdminOrderNoteRequest;
import org.mannerth.starxcar.order.admin.request.AdminOrderReasonRequest;
import org.mannerth.starxcar.order.admin.response.*;
import org.mannerth.starxcar.order.response.OrderDetailResponse;
import org.mannerth.starxcar.order.response.OrderStatusTransitionResponse;
import org.mannerth.starxcar.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/admin/orders")
@Tag(name = "管理端订单监控", description = "管理员查看订单、预约运营、追加备注及执行受控人工干预")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {
    private final AdminOrderService adminOrderService;
    private final UserMapper userMapper;

    public AdminOrderController(AdminOrderService adminOrderService, UserMapper userMapper) {
        this.adminOrderService = adminOrderService;
        this.userMapper = userMapper;
    }

    @GetMapping
    @Operation(summary = "分页查询运营订单", description = "仅限 active admin；按订单号、订单类型、状态、乘客或司机关键字、创建时间和预约时间筛选，返回脱敏运营列表。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "查询成功"), @ApiResponse(responseCode = "400", description = "筛选条件无效"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "非管理员或账号不可用")})
    public AdminOrderPageResponse list(@AuthenticationPrincipal String principal,
                                       @RequestParam(defaultValue = "1") @Min(1) long page,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
                                       @RequestParam(required = false) String orderNo,
                                       @RequestParam(required = false) OrderType orderType,
                                       @RequestParam(required = false) OrderStatus status,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdFrom,
                                       @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdTo,
                                       @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime scheduledFrom,
                                       @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime scheduledTo) {
        requireActiveAdmin(principal);
        return adminOrderService.list(page, size, orderNo, orderType, status, keyword, createdFrom, createdTo, scheduledFrom, scheduledTo);
    }

    @GetMapping("/summary")
    @Operation(summary = "获取订单运营摘要", description = "仅限 active admin；按创建时间范围汇总即时/预约订单、各订单状态和已完成订单金额。")
    public AdminOrderSummaryMetricsResponse summary(@AuthenticationPrincipal String principal,
                                                     @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
                                                     @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to) {
        requireActiveAdmin(principal);
        return adminOrderService.summary(from, to);
    }

    @GetMapping("/{orderNo}")
    @Operation(summary = "获取运营订单详情", description = "仅限 active admin；返回订单地点、司机车辆、状态事件和费用，不记录读取审计以避免轮询产生噪声。")
    public OrderDetailResponse detail(@AuthenticationPrincipal String principal, @PathVariable String orderNo) {
        return adminOrderService.detail(requireActiveAdmin(principal), orderNo);
    }

    @GetMapping("/{orderNo}/fare-items")
    @Operation(summary = "查询订单计费明细", description = "仅限 active admin；返回订单完成后生成的分段费用明细。")
    public List<AdminOrderFareItemResponse> fareItems(@AuthenticationPrincipal String principal, @PathVariable String orderNo) {
        requireActiveAdmin(principal); return adminOrderService.fareItems(orderNo);
    }

    @GetMapping("/{orderNo}/notes")
    @Operation(summary = "查询运营备注", description = "仅限 active admin；按创建顺序读取追加式运营备注。")
    public List<AdminOrderNoteResponse> notes(@AuthenticationPrincipal String principal, @PathVariable String orderNo) {
        requireActiveAdmin(principal); return adminOrderService.notes(orderNo);
    }

    @GetMapping("/{orderNo}/driver-location")
    @Operation(summary = "查询订单司机最后位置", description = "仅限 active admin；返回已分配司机的最后上报位置和时间，不提供历史轨迹。")
    public AdminDriverLocationResponse driverLocation(@AuthenticationPrincipal String principal, @PathVariable String orderNo) {
        requireActiveAdmin(principal); return adminOrderService.driverLocation(orderNo);
    }

    @PostMapping("/{orderNo}/cancel")
    @Operation(summary = "管理员取消待处理订单", description = "仅限 active admin；只允许取消 SCHEDULED 或 PENDING 订单。必须填写原因，事务内使用版本号更新订单、写 CANCEL 事件与管理员操作审计。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "取消成功"), @ApiResponse(responseCode = "400", description = "原因无效"), @ApiResponse(responseCode = "409", description = "订单状态不允许取消或已变化")})
    public OrderStatusTransitionResponse cancel(@AuthenticationPrincipal String principal, @PathVariable String orderNo,
                                                @Valid @RequestBody AdminOrderReasonRequest request) {
        return adminOrderService.cancel(requireActiveAdmin(principal), orderNo, request.reason());
    }

    @PostMapping("/{orderNo}/dispatch")
    @Operation(summary = "提前派发预约订单", description = "仅限 active admin；只允许 SCHEDULED 预约订单提前转为 PENDING。必须填写原因，事务内写 DISPATCH 事件与管理员操作审计。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "派单成功"), @ApiResponse(responseCode = "400", description = "原因无效"), @ApiResponse(responseCode = "409", description = "订单不是可提前派单的预约订单或状态已变化")})
    public OrderStatusTransitionResponse dispatch(@AuthenticationPrincipal String principal, @PathVariable String orderNo,
                                                  @Valid @RequestBody AdminOrderReasonRequest request) {
        return adminOrderService.dispatchReservation(requireActiveAdmin(principal), orderNo, request.reason());
    }

    @PostMapping("/{orderNo}/notes")
    @Operation(summary = "追加运营备注", description = "仅限 active admin；备注不可修改或删除，并写入管理员操作审计。")
    public AdminOrderNoteResponse addNote(@AuthenticationPrincipal String principal, @PathVariable String orderNo,
                                          @Valid @RequestBody AdminOrderNoteRequest request) {
        return adminOrderService.addNote(requireActiveAdmin(principal), orderNo, request.content());
    }

    private Long requireActiveAdmin(String principal) {
        Long adminId = CurrentUser.id(principal);
        User user = userMapper.selectById(adminId);
        if (user == null || user.getType() != UserType.admin || !"active".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理员账号不可用");
        }
        return adminId;
    }
}
