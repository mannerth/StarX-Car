package org.mannerth.starxcar.order.complaint.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComplaintCreateRequest(@NotBlank @Size(max = 40) String complaintType, @NotBlank @Size(max = 1000) String content) {}
