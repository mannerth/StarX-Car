package org.mannerth.starxcar.driver.request;

import jakarta.validation.constraints.NotNull;






public record CarBindingRequest(@NotNull Long carId) {
}