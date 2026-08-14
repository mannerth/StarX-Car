package org.mannerth.starxcar.order.rating;

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
import org.mannerth.starxcar.order.rating.request.RatingSubmitRequest;
import org.mannerth.starxcar.order.rating.response.OrderRatingResponse;
import org.mannerth.starxcar.order.rating.response.RatingPageResponse;
import org.mannerth.starxcar.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Validated
@Tag(name = "评价", description = "订单完成后乘客对司机进行 1-5 星评分；乘客可查自己的评价，司机可查收到的评价")
@SecurityRequirement(name = "bearerAuth")
public class RatingController {
    private final RatingService ratingService;
    private final UserMapper userMapper;

    public RatingController(RatingService ratingService, UserMapper userMapper) {
        this.ratingService = ratingService;
        this.userMapper = userMapper;
    }

    @PostMapping("/api/orders/{orderNo}/rating")
    @PreAuthorize("hasRole('PASSENGER')")
    @Operation(summary = "提交订单评价", description = "仅限 passenger；仅可评价本人已完成且未超过 7 天时限的订单，一个订单只能评价一次。评价同时更新司机的缓存平均评分和评价次数。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "评价成功"),
            @ApiResponse(responseCode = "400", description = "评分为空或不在 1-5 之间，或评价文字超过 500 字"),
            @ApiResponse(responseCode = "401", description = "未登录或令牌无效"),
            @ApiResponse(responseCode = "403", description = "非乘客角色"),
            @ApiResponse(responseCode = "404", description = "订单不存在或不属于当前乘客"),
            @ApiResponse(responseCode = "409", description = "订单未完成、超过评价时限或已评价")})
    public OrderRatingResponse submit(@AuthenticationPrincipal String principal, @PathVariable String orderNo,
                                      @Valid @RequestBody RatingSubmitRequest request) {
        return ratingService.submit(CurrentUser.id(principal), orderNo, request);
    }

    @GetMapping("/api/orders/{orderNo}/rating")
    @Operation(summary = "查询订单评价", description = "乘客只能查看本人订单，司机只能查看自己接的订单，管理员可查看任意订单；无归属权限时返回 404。未评价返回 rated=false。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未登录或令牌无效"),
            @ApiResponse(responseCode = "403", description = "账号不可用"),
            @ApiResponse(responseCode = "404", description = "订单不存在或无读取权限")})
    public OrderRatingResponse get(@AuthenticationPrincipal String principal, @PathVariable String orderNo) {
        Long userId = CurrentUser.id(principal);
        User user = userMapper.selectById(userId);
        if (user == null || !"active".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已停用或注销");
        }
        return ratingService.getOrderRating(userId, user.getType(), orderNo);
    }

    @GetMapping("/api/drivers/me/ratings")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "分页查询我收到的评价", description = "仅限 driver；按提交时间倒序返回当前司机收到的全部评价，含被评价订单号。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "分页参数无效"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "非司机角色")})
    public RatingPageResponse myDriverRatings(@AuthenticationPrincipal String principal,
                                              @RequestParam(defaultValue = "1") @Min(1) long page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size) {
        return ratingService.listDriverRatings(CurrentUser.id(principal), page, size);
    }

    @GetMapping("/api/passengers/me/ratings")
    @PreAuthorize("hasRole('PASSENGER')")
    @Operation(summary = "分页查询我提交的评价", description = "仅限 passenger；按提交时间倒序返回当前乘客提交的全部评价，含被评价订单号。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "分页参数无效"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "非乘客角色")})
    public RatingPageResponse myPassengerRatings(@AuthenticationPrincipal String principal,
                                                 @RequestParam(defaultValue = "1") @Min(1) long page,
                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size) {
        return ratingService.listPassengerRatings(CurrentUser.id(principal), page, size);
    }
}