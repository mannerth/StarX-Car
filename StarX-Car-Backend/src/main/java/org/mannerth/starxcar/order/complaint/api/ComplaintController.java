package org.mannerth.starxcar.order.complaint.api;

import org.mannerth.starxcar.order.complaint.application.ComplaintService;
import org.mannerth.starxcar.order.complaint.request.ComplaintCreateRequest;
import org.mannerth.starxcar.order.complaint.request.ComplaintProcessRequest;
import org.mannerth.starxcar.order.complaint.response.ComplaintResponse;
import org.mannerth.starxcar.order.complaint.response.ComplaintPageResponse;
import org.mannerth.starxcar.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "订单投诉", description = "订单参与者投诉与管理员售后处理")
@SecurityRequirement(name = "bearerAuth")
public class ComplaintController {
    private final ComplaintService complaintService;
    public ComplaintController(ComplaintService complaintService) { this.complaintService = complaintService; }
    @PostMapping("/api/orders/{orderNo}/complaints") @PreAuthorize("hasAnyRole('PASSENGER','DRIVER')")
    @Operation(summary = "创建订单投诉", description = "订单乘客或接单司机可在订单结束七天内创建投诉。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "创建成功"), @ApiResponse(responseCode = "400", description = "内容无效"), @ApiResponse(responseCode = "404", description = "订单不存在或无权访问"), @ApiResponse(responseCode = "409", description = "投诉时限已过")})
    public ComplaintResponse create(@AuthenticationPrincipal String principal, @PathVariable String orderNo, @Valid @RequestBody ComplaintCreateRequest request) {
        return complaintService.create(CurrentUser.id(principal), orderNo, request.complaintType(), request.content());
    }
    @GetMapping("/api/complaints/me") @PreAuthorize("hasAnyRole('PASSENGER','DRIVER')")
    @Operation(summary = "查询我的投诉", description = "乘客或司机分页查看自己发起的投诉、处理状态、管理员最终处理说明和退款结果；仅返回本人记录。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "查询成功"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "角色无权限")})
    public ComplaintPageResponse mine(@AuthenticationPrincipal String principal, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size) { return complaintService.myComplaints(CurrentUser.id(principal), page, size); }
    @GetMapping("/api/admin/complaints") @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "查询投诉工单", description = "管理员按状态分页查看投诉工单，用于售后运营与退款处理。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "查询成功"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "非管理员")})
    public ComplaintPageResponse list(@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size) { return complaintService.adminComplaints(status, page, size); }
    @GetMapping("/api/admin/complaints/{complaintNo}") @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "查询投诉详情", description = "管理员查看一笔投诉的订单、内容、处理结果和退款金额。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "查询成功"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "非管理员"), @ApiResponse(responseCode = "404", description = "投诉不存在")})
    public ComplaintResponse detail(@PathVariable String complaintNo) { return complaintService.adminDetail(complaintNo); }
    @PostMapping("/api/admin/complaints/{complaintNo}/process") @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "处理投诉", description = "管理员填写处理说明并以仅道歉、部分退款、全额退款或驳回方式结案；处理说明写入追加式日志，退款仅限已支付金额，并原子回补乘客余额和回冲司机余额。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "处理成功"), @ApiResponse(responseCode = "400", description = "处理参数无效"), @ApiResponse(responseCode = "404", description = "投诉不存在"), @ApiResponse(responseCode = "409", description = "投诉已处理、订单未支付或退款金额超限")})
    public ComplaintResponse process(@AuthenticationPrincipal String principal, @PathVariable String complaintNo, @Valid @RequestBody ComplaintProcessRequest request) {
        return complaintService.resolve(CurrentUser.id(principal), complaintNo, request.resolutionType(),
                request.refundAmount(), request.content());
    }
}
