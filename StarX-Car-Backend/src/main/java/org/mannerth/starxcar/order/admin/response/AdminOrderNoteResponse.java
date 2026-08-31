package org.mannerth.starxcar.order.admin.response;

import java.time.LocalDateTime;






public record AdminOrderNoteResponse(Long id, Long adminId, String adminName, String content, LocalDateTime createdTime) {
}