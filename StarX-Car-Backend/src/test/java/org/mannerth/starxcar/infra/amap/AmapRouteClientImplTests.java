package org.mannerth.starxcar.infra.amap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mannerth.starxcar.config.AmapProperties;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AmapRouteClientImplTests {
    private MockRestServiceServer server;
    private AmapRouteClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://amap.test");
        server = MockRestServiceServer.bindTo(builder).build();
        AmapProperties properties = new AmapProperties();
        properties.setKey("test-key");
        client = new AmapRouteClientImpl(builder.build(), properties);
    }

    @Test
    void returnsFirstDrivingRouteAndUsesLongitudeBeforeLatitude() {
        server.expect(requestTo("https://amap.test/v5/direction/driving?key=test-key&origin=116.397128,39.916527&destination=121.473701,31.230416&show_fields=cost"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"status":"1","route":{"paths":[
                          {"distance":"12345","cost":{"duration":"678"}},
                          {"distance":"99999","cost":{"duration":"999"}}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        RouteEstimate estimate = client.estimateDrivingRoute(
                new Gcj02Coordinate(39.916527, 116.397128),
                new Gcj02Coordinate(31.230416, 121.473701));

        assertThat(estimate).isEqualTo(new RouteEstimate(12_345, 678));
        server.verify();
    }

    @Test
    void mapsNoPathAndUpstreamFailuresToControlledServiceUnavailable() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://amap.test/v5/direction/driving")))
                .andRespond(withSuccess("{\"status\":\"1\",\"route\":{\"paths\":[]}}", MediaType.APPLICATION_JSON));

        assertUnavailable();
        server.verify();

        server.reset();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://amap.test/v5/direction/driving")))
                .andRespond(withServerError());

        assertUnavailable();
        server.verify();
    }

    @Test
    void rejectsInvalidCoordinatesBeforeCallingAmap() {
        assertThatThrownBy(() -> new Gcj02Coordinate(91, 116))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Gcj02Coordinate(39, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertUnavailable() {
        assertThatThrownBy(() -> client.estimateDrivingRoute(
                new Gcj02Coordinate(39.916527, 116.397128),
                new Gcj02Coordinate(31.230416, 121.473701)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(503));
    }
}
