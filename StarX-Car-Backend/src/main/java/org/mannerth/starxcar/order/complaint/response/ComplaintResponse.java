package org.mannerth.starxcar.order.complaint.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Complaint information visible to the complainant and administrators. */
public record ComplaintResponse(String complaintNo, String orderNo, String complaintType, String content,
                                String status, String resolutionType, BigDecimal refundAmount,
                                @Schema(description = "管理员最终处理说明；投诉未结案时为空", example = "已核实服务情况并完成退款")
                                String resolutionContent,
                                LocalDateTime createdTime, LocalDateTime resolvedTime) {}
