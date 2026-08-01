package org.mannerth.starxcar.car;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record CarRequest(
        @NotBlank @Size(max = 20) String licenseNumber,
        @NotBlank @Size(max = 20) String carType,
        @NotBlank @Size(max = 50) String brand,
        @NotBlank @Size(max = 20) String color,
        @Schema(description = "车牌省份简称", example = "粤") @NotBlank @Size(min = 1, max = 1) String plateProvince,
        @Schema(description = "车牌发牌机关代号", example = "B") @NotBlank @Pattern(regexp = "^[A-Za-z]$") String plateLetter,
        @Schema(description = "车牌数字和字母部分", example = "12345") @NotBlank @Size(max = 6) String plateSerial,
        @Schema(description = "车辆登记归属地", example = "广东省深圳市") @Size(max = 100) String registrationPlace,
        @Schema(description = "核定载客数", example = "5") @NotNull @Min(1) @Max(9) Integer seatCount,
        @Schema(description = "车辆出厂年份", example = "2023") @Min(1900) @Max(2100) Integer vehicleYear) {
}
