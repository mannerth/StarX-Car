package org.mannerth.starxcar.order.pricing.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;






@Schema(description = "管理员完整提交的计价规则草稿；三个收费类型的所有分段必须一次性提供")
public record PricingRuleWriteRequest(
        @NotBlank @Size(max = 50) @Schema(description = "规则名称；同名新建自动生成下一版本", example = "默认即时叫车规则") String name,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) @Schema(description = "起步价，元", example = "5.00") BigDecimal baseFare,
        @NotNull @Min(0) @Max(4_294_967_295L) @Schema(description = "起步价包含距离，单位米", example = "2000") Long includedDistanceM,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) @Schema(description = "最低消费，元", example = "5.00") BigDecimal minimumFare,
        @NotEmpty @Valid @Schema(description = "完整收费分段集合") List<PricingTierWriteRequest> tiers
) {
    public PricingRuleWriteRequest {
        tiers = tiers == null ? null : List.copyOf(tiers);
    }
}