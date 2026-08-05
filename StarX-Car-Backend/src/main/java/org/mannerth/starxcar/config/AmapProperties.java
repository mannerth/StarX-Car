package org.mannerth.starxcar.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "amap")
public class AmapProperties {
    @NotBlank
    private String key;

    @NotBlank
    private String webServiceBaseUrl = "https://restapi.amap.com";

    @NotBlank
    private String drivingRoutePath = "/v5/direction/driving";

    @Min(100)
    @Max(60_000)
    private int connectTimeoutMs = 3_000;

    @Min(100)
    @Max(60_000)
    private int readTimeoutMs = 5_000;
}
