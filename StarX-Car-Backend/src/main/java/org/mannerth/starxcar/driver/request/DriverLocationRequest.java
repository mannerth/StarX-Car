package org.mannerth.starxcar.driver.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "司机最新 GCJ-02 位置上报")
public record DriverLocationRequest(
        @Schema(description = "GCJ-02 纬度", example = "39.916527")
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") @Digits(integer = 2, fraction = 6) BigDecimal latitude,
        @Schema(description = "GCJ-02 经度", example = "116.397128")
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") @Digits(integer = 3, fraction = 6) BigDecimal longitude,
        @Schema(description = "定位精度，米", example = "8.5")
        @DecimalMin("0.0") @DecimalMax("10000.0") @Digits(integer = 6, fraction = 2) BigDecimal accuracyM,
        @Schema(description = "速度，米/秒", example = "12.3")
        @DecimalMin("0.0") @DecimalMax("100.0") @Digits(integer = 3, fraction = 2) BigDecimal speedMps,
        @Schema(description = "朝向，0 至 360", example = "180.0")
        @DecimalMin("0.0") @DecimalMax("360.0") @Digits(integer = 3, fraction = 2) BigDecimal bearing,
        @Schema(description = "设备记录时间，格式 yyyy-MM-dd HH:mm:ss", example = "2026-08-05 10:00:00")
        @NotNull @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai") LocalDateTime recordedTime
) {
}
