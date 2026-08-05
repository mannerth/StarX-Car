package org.mannerth.starxcar.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.mannerth.starxcar.auth.User;
import org.mannerth.starxcar.auth.UserMapper;
import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.order.response.OrderCompletionResponse;
import org.mannerth.starxcar.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Tag(name = "订单完成与结算", description = "司机完成服务并按订单计价规则快照结算")
@SecurityRequirement(name = "bearerAuth")
public class OrderCompletionController {
    private final OrderCompletionService completionService;
    private final UserMapper userMapper;

    public OrderCompletionController(OrderCompletionService completionService, UserMapper userMapper) {
        this.completionService = completionService;
        this.userMapper = userMapper;
    }

    @PostMapping("/api/orders/{orderNo}/complete")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "完成服务并结算", description = "仅限已接此订单且账号有效的 driver；订单必须处于 IN_SERVICE。服务端以订单锁定的计价规则版本、计费距离、实际服务秒数和等待秒数计算最终金额，写入明细和 COMPLETE 事件后释放司机。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "完成和结算成功"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "非司机角色或账号不可用"), @ApiResponse(responseCode = "404", description = "订单不存在或不属于当前司机"), @ApiResponse(responseCode = "409", description = "订单状态、时间或司机状态不满足完成条件"), @ApiResponse(responseCode = "503", description = "订单使用的历史计价规则不可用")})
    public OrderCompletionResponse complete(@AuthenticationPrincipal String principal, @PathVariable String orderNo) {
        Long userId = CurrentUser.id(principal);
        User user = userMapper.selectById(userId);
        if (user == null || !"active".equals(user.getStatus()) || user.getType() != UserType.driver) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不能完成订单");
        }
        return completionService.complete(userId, orderNo);
    }
}
