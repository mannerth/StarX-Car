package org.mannerth.starxcar.driver;

import jakarta.validation.constraints.NotNull;

public record CarBindingRequest(@NotNull Long carId) {
}
