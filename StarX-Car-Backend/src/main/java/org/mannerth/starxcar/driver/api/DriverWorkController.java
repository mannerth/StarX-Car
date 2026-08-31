package org.mannerth.starxcar.driver.api;

import org.mannerth.starxcar.driver.application.DriverWorkService;
import org.mannerth.starxcar.driver.domain.Driver;
import org.mannerth.starxcar.driver.request.DriverLocationRequest;
import org.mannerth.starxcar.driver.request.DriverWorkStatusRequest;
import org.mannerth.starxcar.security.CurrentUser;
import org.mannerth.starxcar.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;






@RestController
@RequestMapping("/api/drivers/me")
@PreAuthorize("hasRole('DRIVER')")
@Tag(name = "司机出车与位置", description = "司机听单状态和最新 GCJ-02 位置上报")
@SecurityRequirement(name = "bearerAuth")
public class DriverWorkController {
    private final DriverWorkService driverWorkService;

    public DriverWorkController(DriverWorkService driverWorkService) {
        this.driverWorkService = driverWorkService;
    }

    @PutMapping("/work-status")
    @Operation(summary = "切换司机出车状态", description = "仅限审核通过且账号有效的 driver；上线必须有有效当前用车，下线不能存在进行中订单。busy 由后续接单流程维护，客户端不能设置。")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "状态更新成功"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "状态参数无效"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非司机、账号不可用或未审核"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "司机资料不存在"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "当前用车或订单状态不允许切换")})
    public Driver updateWorkStatus(@AuthenticationPrincipal String principal,
                                   @Valid @RequestBody DriverWorkStatusRequest request) {
        return driverWorkService.updateWorkStatus(CurrentUser.id(principal), request.status());
    }

    @PutMapping("/location")
    @Operation(summary = "上报司机最新位置", description = "仅限处于 online 或 busy 的当前司机；只接受 GCJ-02 最新位置，服务端维护接收时间和订单关联，不保存轨迹历史。")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "上报成功"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "坐标、速度、朝向或设备时间无效"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未登录"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "非司机、账号不可用或未审核"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "司机资料不存在"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "司机未出车")})
    public ApiResponse<Void> reportLocation(@AuthenticationPrincipal String principal,
                                            @Valid @RequestBody DriverLocationRequest request) {
        driverWorkService.reportLocation(CurrentUser.id(principal), request);
        return ApiResponse.success(null);
    }
}