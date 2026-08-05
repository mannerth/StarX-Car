package org.mannerth.starxcar.infra.amap;

import java.util.Locale;

/** A validated GCJ-02 coordinate. AMap expects longitude before latitude in query parameters. */
public record Gcj02Coordinate(double latitude, double longitude) {
    public Gcj02Coordinate {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("纬度必须在 -90 到 90 之间");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("经度必须在 -180 到 180 之间");
        }
    }

    String toAmapValue() {
        return String.format(Locale.ROOT, "%.6f,%.6f", longitude, latitude);
    }
}
