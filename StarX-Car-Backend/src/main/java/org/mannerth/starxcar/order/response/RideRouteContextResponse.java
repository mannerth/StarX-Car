package org.mannerth.starxcar.order.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "服务中订单的高德路线规划上下文；客户端以其中 GCJ-02 坐标自行规划路线")
public record RideRouteContextResponse(
        @Schema(description = "接单司机的最后已知位置") DriverLocationResponse driverLocation,
        @Schema(description = "目的地高德 POI ID") String destinationPoiId,
        @Schema(description = "目的地名称", example = "北京首都国际机场") String destinationName,
        @Schema(description = "目的地地址") String destinationAddress,
        @Schema(description = "目的地 GCJ-02 纬度", example = "40.079311") BigDecimal destinationLat,
        @Schema(description = "目的地 GCJ-02 经度", example = "116.603112") BigDecimal destinationLng,
        @Schema(description = "建议司机位置变化超过此距离后重规划，单位米", example = "100") int recommendedRefreshDistanceM,
        @Schema(description = "建议最长重规划间隔，单位秒", example = "60") int recommendedRefreshSeconds
) {
}
