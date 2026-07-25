package org.mannerth.starxcar.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Setter
@Getter
@Validated
@ConfigurationProperties(prefix = "admin")
public class AdminProperties {

    @NotBlank
    @Size(min = 6, max = 72)
    private String defaultPassword;
}
