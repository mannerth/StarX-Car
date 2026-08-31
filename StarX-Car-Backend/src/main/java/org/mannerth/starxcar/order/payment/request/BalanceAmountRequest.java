package org.mannerth.starxcar.order.payment.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Schema(description = "模拟余额金额请求")
public record BalanceAmountRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
        @Schema(description = "金额，元", example = "100.00") BigDecimal amount
) {
}
