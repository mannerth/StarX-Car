package org.mannerth.starxcar.infrastructure.amap;

import org.mannerth.starxcar.config.AmapProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;






@Service
public class AmapRouteClientImpl implements AmapRouteClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(AmapRouteClientImpl.class);

    private final RestClient amapRestClient;
    private final AmapProperties properties;

    public AmapRouteClientImpl(@Qualifier("amapRestClient") RestClient amapRestClient, AmapProperties properties) {
        this.amapRestClient = amapRestClient;
        this.properties = properties;
    }

    @Override
    public RouteEstimate estimateDrivingRoute(Gcj02Coordinate origin, Gcj02Coordinate destination) {
        try {
            AmapDrivingRouteResponse response = amapRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path(properties.getDrivingRoutePath())
                            .queryParam("key", properties.getKey())
                            .queryParam("origin", origin.toAmapValue())
                            .queryParam("destination", destination.toAmapValue())
                            .queryParam("show_fields", "cost")
                            .build())
                    .retrieve()
                    .body(AmapDrivingRouteResponse.class);
            return routeEstimate(response);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            LOGGER.warn("AMap driving-route request failed with upstream status={}", exception.getStatusCode());
            throw unavailable();
        } catch (ResourceAccessException exception) {
            LOGGER.warn("AMap driving-route request failed due to network access");
            throw unavailable();
        } catch (Exception exception) {
            LOGGER.warn("AMap driving-route response could not be processed: type={}",
                    exception.getClass().getSimpleName());
            throw unavailable();
        }
    }

    private RouteEstimate routeEstimate(AmapDrivingRouteResponse response) {
        if (response == null || !"1".equals(response.status()) || response.route() == null
                || response.route().paths() == null || response.route().paths().isEmpty()) {
            LOGGER.warn("AMap driving-route response contained no usable path");
            throw unavailable();
        }
        AmapDrivingRouteResponse.Path path = response.route().paths().getFirst();
        try {
            long distanceM = Long.parseLong(path.distance());
            long durationS = Long.parseLong(path.cost().duration());
            if (distanceM < 0 || durationS < 0) {
                throw new NumberFormatException("negative route metric");
            }
            return new RouteEstimate(distanceM, durationS);
        } catch (NumberFormatException exception) {
            LOGGER.warn("AMap driving-route response contained invalid route metrics");
            throw unavailable();
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "地图路线服务暂时不可用");
    }
}