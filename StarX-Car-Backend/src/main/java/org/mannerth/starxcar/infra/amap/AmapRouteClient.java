package org.mannerth.starxcar.infra.amap;

/** Internal boundary for AMap driving-route estimates. */
public interface AmapRouteClient {

    RouteEstimate estimateDrivingRoute(Gcj02Coordinate origin, Gcj02Coordinate destination);
}
