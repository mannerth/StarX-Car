package org.mannerth.starxcar.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @Schema(description = "当前密码，用于验证本人身份", example = "OldPassword123")
        @NotBlank String currentPassword,
        @Schema(description = "新密码，长度为 6 至 72 个字符", example = "NewPassword123")
        @NotBlank @Size(min = 6, max = 72) String newPassword) {
}
