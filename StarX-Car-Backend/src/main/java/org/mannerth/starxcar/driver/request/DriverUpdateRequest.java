package org.mannerth.starxcar.driver.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;






public record DriverUpdateRequest(
        @NotBlank @Pattern(regexp = "^1\\d{10}$") String phoneNumber,
        @NotBlank @Size(max = 30) String licenseNumber,
        @Schema(description = "身份证号码", example = "110101199001011234") @Pattern(regexp = "^(\\d{15}|\\d{17}[0-9Xx])$") String idCardNo,
        @Schema(description = "驾驶证有效期，格式 yyyy-MM-dd", example = "2030-12-31") LocalDate licenseExpireDate) {
}