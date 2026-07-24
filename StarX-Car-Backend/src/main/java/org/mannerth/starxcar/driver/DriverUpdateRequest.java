package org.mannerth.starxcar.driver;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DriverUpdateRequest(
        @NotBlank @Pattern(regexp = "^1\\d{10}$") String phoneNumber,
        @NotBlank @Size(max = 30) String licenseNumber) {
}
