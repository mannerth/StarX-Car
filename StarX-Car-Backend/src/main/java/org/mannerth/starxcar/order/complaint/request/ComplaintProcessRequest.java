package org.mannerth.starxcar.order.complaint.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ComplaintProcessRequest(
        @NotBlank String resolutionType,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal refundAmount,
        @NotBlank @Size(max = 1000) String content
) {}
