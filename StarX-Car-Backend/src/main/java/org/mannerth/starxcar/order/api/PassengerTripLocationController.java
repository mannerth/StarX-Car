package org.mannerth.starxcar.order.api;

import org.mannerth.starxcar.auth.domain.User;
import org.mannerth.starxcar.auth.infrastructure.UserMapper;
import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.order.application.PassengerTripLocationService;
import org.mannerth.starxcar.order.response.DriverLocationResponse;
import org.mannerth.starxcar.order.response.RideRouteContextResponse;
import org.mannerth.starxcar.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;






@RestController
@Tag(name = "乘客行程位置", description = "乘客查询本人订单司机的最新 GCJ-02 位置与服务中路线规划上下文")
@SecurityRequirement(name = "bearerAuth")
public class PassengerTripLocationController {
    private final PassengerTripLocationService tripLocationService;
    private final UserMapper userMapper;

    public PassengerTripLocationController(PassengerTripLocationService tripLocationService, UserMapper userMapper) {
        this.tripLocationService = tripLocationService;
        this.userMapper = userMapper;
    }

    @GetMapping("/api/orders/{orderNo}/driver-location")
    @PreAuthorize("hasRole('PASSENGER')")
    @Operation(summary = "查看订单司机最新位置", description = "仅限订单乘客；仅在 ACCEPTED、DRIVER_ARRIVED 或 IN_SERVICE 状态返回与该订单关联的司机最后 GCJ-02 位置。建议客户端每 3 至 5 秒轮询；位置超过保鲜窗口时返回 stale=true，不伪造新位置。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "查询成功"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "非乘客角色或账号不可用"), @ApiResponse(responseCode = "404", description = "订单不存在、不属于当前乘客或暂无与订单关联的位置"), @ApiResponse(responseCode = "409", description = "订单状态暂不允许查看司机位置")})
    public DriverLocationResponse driverLocation(@AuthenticationPrincipal String principal, @PathVariable String orderNo) {
        return tripLocationService.getDriverLocation(requireActivePassenger(principal), orderNo);
    }

    @GetMapping("/api/orders/{orderNo}/route-context")
    @PreAuthorize("hasRole('PASSENGER')")
    @Operation(summary = "获取服务中路线规划上下文", description = "仅限订单乘客且订单处于 IN_SERVICE；返回司机最后已知 GCJ-02 位置、目的地 GCJ-02 坐标与名称，以及建议刷新阈值。客户端使用高德 SDK 或服务自行规划路线，后端不保存轨迹或代理路线请求。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "查询成功"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "非乘客角色或账号不可用"), @ApiResponse(responseCode = "404", description = "订单不存在、不属于当前乘客或暂无与订单关联的位置"), @ApiResponse(responseCode = "409", description = "订单未处于 IN_SERVICE")})
    public RideRouteContextResponse routeContext(@AuthenticationPrincipal String principal, @PathVariable String orderNo) {
        return tripLocationService.getRouteContext(requireActivePassenger(principal), orderNo);
    }

    private Long requireActivePassenger(String principal) {
        Long passengerId = CurrentUser.id(principal);
        User user = userMapper.selectById(passengerId);
        if (user == null || user.getType() != UserType.passenger || !"active".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不能查看行程位置");
        }
        return passengerId;
    }
}