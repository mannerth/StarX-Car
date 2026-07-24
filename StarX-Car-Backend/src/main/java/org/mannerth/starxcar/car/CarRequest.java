package org.mannerth.starxcar.car;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CarRequest(
        @NotBlank @Size(max = 20) String licenseNumber,
        @NotBlank @Size(max = 20) String carNumber,
        @NotBlank @Size(max = 20) String carType,
        @NotBlank @Size(max = 50) String brand,
        @NotBlank @Size(max = 20) String color) {
}
