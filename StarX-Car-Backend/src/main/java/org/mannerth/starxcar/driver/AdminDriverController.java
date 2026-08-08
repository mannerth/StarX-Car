package org.mannerth.starxcar.driver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.mannerth.starxcar.auth.User;
import org.mannerth.starxcar.auth.UserMapper;
import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.driver.request.AdminDriverApprovalRequest;
import org.mannerth.starxcar.driver.response.AdminDriverPageResponse;
import org.mannerth.starxcar.driver.response.AdminDriverResponse;
import org.mannerth.starxcar.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Validated
@RequestMapping("/api/admin/drivers")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "后台司机审核", description = "管理员查看司机证件、当前车辆并处理待审核申请")
@SecurityRequirement(name = "bearerAuth")
public class AdminDriverController {
    private final AdminDriverService adminDriverService;
    private final UserMapper userMapper;

    public AdminDriverController(AdminDriverService adminDriverService, UserMapper userMapper) {
        this.adminDriverService = adminDriverService;
        this.userMapper = userMapper;
    }

    @GetMapping
    @Operation(summary = "分页查询待审核司机", description = "仅限状态为 active 的 admin；可按审核状态筛选，并按用户名、姓名、邮箱、手机号、驾驶证号或身份证号搜索，返回证件和当前车辆信息。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "查询成功"), @ApiResponse(responseCode = "400", description = "分页或审核状态参数无效"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "非管理员或账号不可用")})
    public AdminDriverPageResponse list(@AuthenticationPrincipal String principal,
                                        @RequestParam(defaultValue = "1") @Min(1) long page,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) @Pattern(regexp = "pending|approved|rejected") String approvalStatus) {
        requireActiveAdmin(principal);
        return adminDriverService.list(page, size, keyword, approvalStatus);
    }

    @PutMapping("/{driverId}/approval")
    @Operation(summary = "审核司机申请", description = "仅限状态为 active 的 admin；将 pending 司机一次性审核为 approved 或 rejected。已审核司机、不可用账号或不存在的司机会被拒绝，审核后司机保持 offline。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "审核成功"), @ApiResponse(responseCode = "400", description = "审核结果无效"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "非管理员或账号不可用"), @ApiResponse(responseCode = "404", description = "司机或司机用户不存在"), @ApiResponse(responseCode = "409", description = "司机已审核或账号状态不可用")})
    public AdminDriverResponse review(@AuthenticationPrincipal String principal, @PathVariable Long driverId,
                                      @Valid @RequestBody AdminDriverApprovalRequest request) {
        requireActiveAdmin(principal);
        return adminDriverService.review(driverId, request.approvalStatus());
    }

    private void requireActiveAdmin(String principal) {
        User user = userMapper.selectById(CurrentUser.id(principal));
        if (user == null || user.getType() != UserType.admin || !"active".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不能审核司机");
        }
    }
}
