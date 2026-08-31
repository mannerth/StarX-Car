package org.mannerth.starxcar.auth.request;

import org.mannerth.starxcar.constant.GenderEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;






public record UserProfileUpdateRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @Email @Size(max = 50) String email,
        @NotNull GenderEnum gender,
        @Schema(description = "真实姓名", example = "张三") @Size(max = 50) String realName,
        @Schema(description = "头像 URL", example = "https://example.com/avatar.png") @Size(max = 255) String avatarUrl) {
}