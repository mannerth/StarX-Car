package org.mannerth.starxcar.auth.request;

import org.mannerth.starxcar.constant.GenderEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;






/** Input accepted when an administrator changes a user's basic account details. */
public record AdminUserUpdateRequest(
        @Schema(description = "登录用户名，系统内唯一", example = "alice")
        @NotBlank @Size(min = 3, max = 50) String username,
        @Schema(description = "电子邮箱，可为空", example = "alice@example.com")
        @Email @Size(max = 50) String email,
        @Schema(description = "性别", example = "female")
        @NotNull GenderEnum gender,
        @Schema(description = "真实姓名", example = "张三") @Size(max = 50) String realName,
        @Schema(description = "头像 URL", example = "https://example.com/avatar.png") @Size(max = 255) String avatarUrl,
        @Schema(description = "账号状态；不传时保持原状态", example = "active")
        @Pattern(regexp = "^(active|disabled|cancelled)$") String status) {
}