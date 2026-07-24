package org.mannerth.starxcar.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.mannerth.starxcar.constant.GenderEnum;

public record UserProfileUpdateRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @Email @Size(max = 50) String email,
        @NotNull GenderEnum gender) {
}
