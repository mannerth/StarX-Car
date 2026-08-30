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
@ConfigurationProperties(prefix = "reservation")
public class ReservationProperties {

    @Min(0)
    @Max(1440)
    private int dispatchAheadMinutes = 30;

    @Min(5000)
    @Max(600000)
    private int dispatchIntervalMs = 30000;

    @Min(1)
    @Max(1440)
    private int minLeadMinutes = 30;

    @Min(1)
    @Max(30)
    private int maxAdvanceDays = 7;

    @Min(1)
    @Max(1440)
    private int expireGraceMinutes = 30;
}
