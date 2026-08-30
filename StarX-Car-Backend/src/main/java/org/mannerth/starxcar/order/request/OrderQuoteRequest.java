package org.mannerth.starxcar.order.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.mannerth.starxcar.order.OrderType;

import java.time.LocalDateTime;

@Schema(description = "叫车报价请求（即时/预约）")
public record OrderQuoteRequest(
        @NotNull @Valid @Schema(description = "上车点") PlaceRequest pickup,
        @NotNull @Valid @Schema(description = "目的地") PlaceRequest destination,
        @Schema(description = "订单类型：INSTANT 即时单、SCHEDULED 预约单；缺省为 INSTANT", example = "INSTANT") OrderType orderType,
        @Schema(description = "预约上车时间（yyyy-MM-dd HH:mm:ss，Asia/Shanghai）；SCHEDULED 必填且须在允许时间窗口内，INSTANT 必须为空")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai") LocalDateTime scheduledPickupTime
) {
    public OrderQuoteRequest {
        orderType = orderType == null ? OrderType.INSTANT : orderType;
    }

    public OrderQuoteRequest(PlaceRequest pickup, PlaceRequest destination) {
        this(pickup, destination, OrderType.INSTANT, null);
    }
}
