package org.mannerth.starxcar.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import org.mannerth.starxcar.constant.GenderEnum;

public record PassengerRegistrationRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Size(min = 6, max = 72) String password,
        @Email @Size(max = 50) String email,
        @NotNull GenderEnum gender,
        @NotBlank @Pattern(regexp = "^1\\d{10}$") String phoneNumber,
        @Schema(description = "真实姓名，可在个人资料中补充", example = "张三") @Size(max = 50) String realName,
        @Schema(description = "头像 URL", example = "https://example.com/avatar.png") @Size(max = 255) String avatarUrl) {
}
