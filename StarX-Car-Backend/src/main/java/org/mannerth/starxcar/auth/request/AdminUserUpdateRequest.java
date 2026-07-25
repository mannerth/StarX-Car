package org.mannerth.starxcar.auth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.mannerth.starxcar.constant.GenderEnum;

/** Input accepted when an administrator changes a user's basic account details. */
public record AdminUserUpdateRequest(
        @Schema(description = "登录用户名，系统内唯一", example = "alice")
        @NotBlank @Size(min = 3, max = 50) String username,
        @Schema(description = "电子邮箱，可为空", example = "alice@example.com")
        @Email @Size(max = 50) String email,
        @Schema(description = "性别", example = "female")
        @NotNull GenderEnum gender) {
}
