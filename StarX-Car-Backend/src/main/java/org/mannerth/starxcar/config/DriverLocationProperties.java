package org.mannerth.starxcar.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;






@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "driver-location")
public class DriverLocationProperties {
    @Min(1)
    @Max(300)
    private int freshnessSeconds = 30;

    @Min(0)
    @Max(600)
    private int maxRecordedTimeFutureSeconds = 60;
}