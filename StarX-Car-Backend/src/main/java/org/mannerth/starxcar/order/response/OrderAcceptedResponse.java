package org.mannerth.starxcar.order.response;

import org.mannerth.starxcar.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;




@Schema(description = "司机抢单结果")
public record OrderAcceptedResponse(
        @Schema(description = "对外订单号", example = "O2026080500000000000") String orderNo,
        @Schema(description = "抢单后的订单状态", example = "ACCEPTED") OrderStatus status,
        @Schema(description = "本次接单使用的车辆 ID", example = "7") Long carId,
        @Schema(description = "服务端接单时间", example = "2026-08-05 10:00:00") LocalDateTime acceptedTime
) {
}