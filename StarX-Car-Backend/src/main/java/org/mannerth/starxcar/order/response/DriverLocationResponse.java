package org.mannerth.starxcar.order.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;




@Schema(description = "订单司机的最新 GCJ-02 位置，不包含历史轨迹")
public record DriverLocationResponse(
        @Schema(description = "GCJ-02 纬度", example = "39.916527") BigDecimal latitude,
        @Schema(description = "GCJ-02 经度", example = "116.397128") BigDecimal longitude,
        @Schema(description = "定位精度，单位米", example = "8.50") BigDecimal accuracyM,
        @Schema(description = "行驶速度，单位米每秒", example = "12.30") BigDecimal speedMps,
        @Schema(description = "朝向，范围 0 至 360 度", example = "180.00") BigDecimal bearing,
        @Schema(description = "司机设备记录位置的时间，格式 yyyy-MM-dd HH:mm:ss", example = "2026-08-05 10:00:00") LocalDateTime recordedTime,
        @Schema(description = "服务端接收位置的时间，格式 yyyy-MM-dd HH:mm:ss", example = "2026-08-05 10:00:03") LocalDateTime receivedTime,
        @Schema(description = "位置是否超过服务端保鲜窗口；为 true 时仍返回最后位置，客户端不应将其视为实时位置") boolean stale
) {
}