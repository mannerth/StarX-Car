package org.mannerth.starxcar.infrastructure.amap;







/** Driving route distance in meters and estimated duration in seconds. */
public record RouteEstimate(long distanceM, long durationS) {
    public RouteEstimate {
        if (distanceM < 0 || durationS < 0) {
            throw new IllegalArgumentException("路线距离和时长不能为负数");
        }
    }
}