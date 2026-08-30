package org.mannerth.starxcar.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.mannerth.starxcar.order.request.OrderQuoteRequest;
import org.mannerth.starxcar.order.response.OrderCreatedResponse;
import org.mannerth.starxcar.order.response.OrderQuoteResponse;
import org.mannerth.starxcar.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "即时与预约订单", description = "乘客即时叫车与预约叫车的报价、创建订单；接单和行程操作沿用即时订单状态机")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/quote")
    @PreAuthorize("hasRole('PASSENGER')")
    @Operation(summary = "获取叫车报价", description = "仅限 passenger；服务端使用高德 GCJ-02 驾车路线和当前默认规则计算预估金额。orderType=SCHEDULED 时请求必须携带预约时间，且须处于允许的预约窗口内。报价仅用于展示，下单时会重新规划和重新计价。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "报价成功"),
            @ApiResponse(responseCode = "400", description = "地点、坐标或预约时间无效"),
            @ApiResponse(responseCode = "401", description = "未登录或令牌无效"),
            @ApiResponse(responseCode = "403", description = "非乘客角色"),
            @ApiResponse(responseCode = "503", description = "路线或默认计价规则不可用")})
    public OrderQuoteResponse quote(@Valid @RequestBody OrderQuoteRequest request) {
        return orderService.quote(request);
    }

    @PostMapping
    @PreAuthorize("hasRole('PASSENGER')")
    @Operation(summary = "创建叫车订单", description = "仅限 passenger；事务内锁定当前乘客、检查未结束订单并重新请求路线与规则，然后写入订单及 CREATE 事件。orderType=SCHEDULED 时创建 SCHEDULED 预约订单，等待调度器到派单窗口转入 PENDING；客户端金额不会被采用。")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "订单创建成功"),
            @ApiResponse(responseCode = "400", description = "地点、坐标或预约时间无效"),
            @ApiResponse(responseCode = "401", description = "未登录或令牌无效"),
            @ApiResponse(responseCode = "403", description = "非乘客角色或账号不可用"),
            @ApiResponse(responseCode = "404", description = "乘客资料不存在"),
            @ApiResponse(responseCode = "409", description = "当前存在未结束订单或未结束的预约订单"),
            @ApiResponse(responseCode = "503", description = "路线或默认计价规则不可用")})
    public ResponseEntity<OrderCreatedResponse> create(@AuthenticationPrincipal String principal,
                                                        @Valid @RequestBody OrderQuoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(CurrentUser.id(principal), request));
    }
}
