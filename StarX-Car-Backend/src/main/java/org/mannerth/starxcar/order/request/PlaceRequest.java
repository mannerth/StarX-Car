package org.mannerth.starxcar.order.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "高德 GCJ-02 地点")
public record PlaceRequest(
        @Schema(description = "高德 POI ID", example = "B000A83M61")
        @Size(max = 64) String poiId,
        @Schema(description = "地点名称", example = "天安门")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "地点地址", example = "北京市东城区东长安街")
        @Size(max = 255) String address,
        @Schema(description = "GCJ-02 纬度", example = "39.916527")
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") @Digits(integer = 2, fraction = 6) BigDecimal latitude,
        @Schema(description = "GCJ-02 经度", example = "116.397128")
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") @Digits(integer = 3, fraction = 6) BigDecimal longitude
) {
}
