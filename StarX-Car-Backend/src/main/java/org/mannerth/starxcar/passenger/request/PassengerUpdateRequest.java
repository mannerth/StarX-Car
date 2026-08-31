package org.mannerth.starxcar.passenger.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;




public record PassengerUpdateRequest(@NotBlank @Pattern(regexp = "^1\\d{10}$") String phoneNumber) {
}