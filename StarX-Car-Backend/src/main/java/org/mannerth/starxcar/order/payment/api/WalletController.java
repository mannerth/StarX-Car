package org.mannerth.starxcar.order.payment.api;

import org.mannerth.starxcar.auth.domain.User;
import org.mannerth.starxcar.auth.infrastructure.UserMapper;
import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.order.payment.application.WalletService;
import org.mannerth.starxcar.order.payment.request.BalanceAmountRequest;
import org.mannerth.starxcar.order.payment.response.BalanceResponse;
import org.mannerth.starxcar.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Tag(name = "余额账户", description = "乘客模拟充值、司机模拟提现与余额查询")
@SecurityRequirement(name = "bearerAuth")
public class WalletController {
    private final WalletService walletService;
    private final UserMapper userMapper;

    public WalletController(WalletService walletService, UserMapper userMapper) {
        this.walletService = walletService;
        this.userMapper = userMapper;
    }

    @GetMapping("/api/wallet/me")
    @PreAuthorize("hasAnyRole('PASSENGER', 'DRIVER')")
    @Operation(summary = "查询本人余额", description = "仅限有效乘客或司机；首次查询会创建余额为零的账户。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "查询成功"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "账号无效或角色不支持")})
    public BalanceResponse balance(@AuthenticationPrincipal String principal) {
        return walletService.balance(requireActiveUser(CurrentUser.id(principal)).getId());
    }

    @PostMapping("/api/passengers/me/wallet/recharge")
    @PreAuthorize("hasRole('PASSENGER')")
    @Operation(summary = "模拟充值", description = "仅限有效乘客；不接第三方支付，提交金额后立即增加本人余额并记录不可修改账本流水。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "充值成功"), @ApiResponse(responseCode = "400", description = "金额无效"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "账号无效或非乘客")})
    public BalanceResponse recharge(@AuthenticationPrincipal String principal, @Valid @RequestBody BalanceAmountRequest request) {
        User user = requireActiveUser(CurrentUser.id(principal));
        if (user.getType() != UserType.passenger) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不是乘客");
        return walletService.recharge(user.getId(), request.amount());
    }

    @PostMapping("/api/drivers/me/wallet/withdrawals")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "模拟提现", description = "仅限有效司机；立即从司机余额扣除并记录账本。提现后余额不得为负数，余额不足返回 409。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "提现成功"), @ApiResponse(responseCode = "400", description = "金额无效"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "账号无效或非司机"), @ApiResponse(responseCode = "409", description = "余额不足或余额已变化")})
    public BalanceResponse withdraw(@AuthenticationPrincipal String principal, @Valid @RequestBody BalanceAmountRequest request) {
        User user = requireActiveUser(CurrentUser.id(principal));
        if (user.getType() != UserType.driver) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不是司机");
        return walletService.withdraw(user.getId(), request.amount());
    }

    private User requireActiveUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !"active".equals(user.getStatus())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已停用或注销");
        return user;
    }
}
