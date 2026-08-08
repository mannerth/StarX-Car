package org.mannerth.starxcar.driver.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.mannerth.starxcar.driver.DriverApprovalDecision;

@Schema(description = "管理员司机审核决定")
public record AdminDriverApprovalRequest(
        @NotNull @Schema(description = "审核结果，只允许通过或驳回", example = "approved")
        DriverApprovalDecision approvalStatus
) {
}
