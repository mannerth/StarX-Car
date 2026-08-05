package org.mannerth.starxcar.order.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(description = "即时叫车报价请求")
public record OrderQuoteRequest(
        @NotNull @Valid @Schema(description = "上车点") PlaceRequest pickup,
        @NotNull @Valid @Schema(description = "目的地") PlaceRequest destination
) {
}
