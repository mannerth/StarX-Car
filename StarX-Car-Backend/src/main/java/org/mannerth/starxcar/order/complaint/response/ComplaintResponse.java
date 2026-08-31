package org.mannerth.starxcar.order.complaint.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Complaint information visible to the complainant and administrators. */
public record ComplaintResponse(String complaintNo, String orderNo, String complaintType, String content,
                                String status, String resolutionType, BigDecimal refundAmount,
                                LocalDateTime createdTime, LocalDateTime resolvedTime) {}
