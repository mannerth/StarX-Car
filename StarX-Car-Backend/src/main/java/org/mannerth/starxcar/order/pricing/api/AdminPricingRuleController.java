package org.mannerth.starxcar.order.pricing.api;

import org.mannerth.starxcar.auth.domain.User;
import org.mannerth.starxcar.auth.infrastructure.UserMapper;
import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.order.domain.PricingRuleStatus;
import org.mannerth.starxcar.order.pricing.application.AdminPricingRuleService;
import org.mannerth.starxcar.order.pricing.request.PricingRuleActivationRequest;
import org.mannerth.starxcar.order.pricing.request.PricingRuleWriteRequest;
import org.mannerth.starxcar.order.pricing.response.PricingRulePageResponse;
import org.mannerth.starxcar.order.pricing.response.PricingRuleResponse;
import org.mannerth.starxcar.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;






@RestController
@Validated
@RequestMapping("/api/admin/pricing-rules")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理员计价规则", description = "管理员维护版本化分段计价规则、默认规则与归档状态")
@SecurityRequirement(name = "bearerAuth")
public class AdminPricingRuleController {
    private final AdminPricingRuleService pricingRuleService;
    private final UserMapper userMapper;

    public AdminPricingRuleController(AdminPricingRuleService pricingRuleService, UserMapper userMapper) {
        this.pricingRuleService = pricingRuleService;
        this.userMapper = userMapper;
    }

    @GetMapping
    @Operation(summary = "分页查询计价规则", description = "仅限状态为 active 的 admin；按创建时间倒序查询全部规则，可按规则状态筛选，并返回完整分段。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "查询成功"), @ApiResponse(responseCode = "400", description = "分页或状态参数无效"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "非管理员或账号不可用")})
    public PricingRulePageResponse list(@AuthenticationPrincipal String principal,
                                        @RequestParam(defaultValue = "1") @Min(1) long page,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
                                        @RequestParam(required = false) PricingRuleStatus status) {
        requireActiveAdmin(principal);
        return pricingRuleService.list(page, size, status);
    }

    @PostMapping
    @Operation(summary = "新建计价规则草稿", description = "仅限状态为 active 的 admin；完整校验三类连续分段后创建 draft。同名规则在事务中生成下一版本，初始不作为默认规则。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "草稿创建成功"), @ApiResponse(responseCode = "400", description = "规则字段或分段函数无效"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "非管理员或账号不可用"), @ApiResponse(responseCode = "409", description = "并发版本冲突")})
    public PricingRuleResponse create(@AuthenticationPrincipal String principal,
                                      @Valid @RequestBody PricingRuleWriteRequest request) {
        return pricingRuleService.create(requireActiveAdmin(principal), request);
    }

    @GetMapping("/{ruleId}")
    @Operation(summary = "查看计价规则", description = "仅限状态为 active 的 admin；返回指定规则、状态、审计字段和完整分段。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "查询成功"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "非管理员或账号不可用"), @ApiResponse(responseCode = "404", description = "规则不存在")})
    public PricingRuleResponse get(@AuthenticationPrincipal String principal, @PathVariable Long ruleId) {
        requireActiveAdmin(principal);
        return pricingRuleService.get(ruleId);
    }

    @PutMapping("/{ruleId}")
    @Operation(summary = "更新计价规则草稿", description = "仅限状态为 active 的 admin；仅未被订单引用的 draft 可更新，必须完整替换三个收费类型的全部分段，规则名称和版本不可修改。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "草稿更新成功"), @ApiResponse(responseCode = "400", description = "规则字段或分段函数无效"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "非管理员或账号不可用"), @ApiResponse(responseCode = "404", description = "规则不存在"), @ApiResponse(responseCode = "409", description = "规则不是可编辑草稿、已被订单引用或发生并发变化")})
    public PricingRuleResponse update(@AuthenticationPrincipal String principal, @PathVariable Long ruleId,
                                      @Valid @RequestBody PricingRuleWriteRequest request) {
        return pricingRuleService.updateDraft(requireActiveAdmin(principal), ruleId, request);
    }

    @PostMapping("/{ruleId}/activate")
    @Operation(summary = "激活或设为默认计价规则", description = "仅限状态为 active 的 admin；完整有效的 draft 可激活，已启用的非默认规则可通过 setAsDefault=true 提升为新订单唯一默认规则。切换在同一事务内停用旧默认规则，不影响历史订单。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "规则状态更新成功"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "非管理员或账号不可用"), @ApiResponse(responseCode = "404", description = "规则不存在"), @ApiResponse(responseCode = "409", description = "规则状态不允许该操作、草稿无效或发生并发状态变化")})
    public PricingRuleResponse activate(@AuthenticationPrincipal String principal, @PathVariable Long ruleId,
                                        @RequestBody(required = false) PricingRuleActivationRequest request) {
        return pricingRuleService.activate(requireActiveAdmin(principal), ruleId, request);
    }

    @PostMapping("/{ruleId}/archive")
    @Operation(summary = "归档计价规则", description = "仅限状态为 active 的 admin；归档只改变规则状态，不删除规则或分段。当前活动默认规则不可归档，已被订单引用的历史规则仍可读取。")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "归档成功"), @ApiResponse(responseCode = "401", description = "未登录"), @ApiResponse(responseCode = "403", description = "非管理员或账号不可用"), @ApiResponse(responseCode = "404", description = "规则不存在"), @ApiResponse(responseCode = "409", description = "当前默认规则或已归档规则不可归档")})
    public PricingRuleResponse archive(@AuthenticationPrincipal String principal, @PathVariable Long ruleId) {
        return pricingRuleService.archive(requireActiveAdmin(principal), ruleId);
    }

    private Long requireActiveAdmin(String principal) {
        Long adminId = CurrentUser.id(principal);
        User user = userMapper.selectById(adminId);
        if (user == null || user.getType() != UserType.admin || !"active".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不能管理计价规则");
        }
        return adminId;
    }
}
