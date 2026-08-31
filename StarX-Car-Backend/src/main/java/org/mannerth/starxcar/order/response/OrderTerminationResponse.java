package org.mannerth.starxcar.order.response;

import org.mannerth.starxcar.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "服务中异常结束后的实际结算结果")
public record OrderTerminationResponse(
        String orderNo,
        OrderStatus status,
        LocalDateTime endedTime,
        Long actualDurationS,
        Long billingDistanceM,
        BigDecimal totalFare
) {
}
