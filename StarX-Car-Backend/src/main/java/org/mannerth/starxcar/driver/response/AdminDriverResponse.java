package org.mannerth.starxcar.driver.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理员审核司机时使用的用户、证件和当前车辆资料")
public record AdminDriverResponse(
        Long id,
        String username,
        String realName,
        String userStatus,
        String phoneNumber,
        String displayPhone,
        String licenseNumber,
        @Schema(description = "身份证号码，仅管理员审核页面可见") String idCardNo,
        LocalDate licenseExpireDate,
        BigDecimal rating,
        String approvalStatus,
        String workStatus,
        Long carId,
        String carNumber,
        String carBrand,
        String carColor,
        LocalDateTime createTime
) {
}
