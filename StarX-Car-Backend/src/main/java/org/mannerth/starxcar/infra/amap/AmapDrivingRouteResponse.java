package org.mannerth.starxcar.infra.amap;

import java.util.List;

record AmapDrivingRouteResponse(String status, Route route) {
    record Route(List<Path> paths) {
    }

    record Path(String distance, Cost cost) {
    }

    record Cost(String duration) {
    }
}
