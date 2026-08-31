package org.mannerth.starxcar.order.payment.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "当前用户余额")
public record BalanceResponse(@Schema(description = "可用余额，元；乘客可为负数") BigDecimal balance) {
}
