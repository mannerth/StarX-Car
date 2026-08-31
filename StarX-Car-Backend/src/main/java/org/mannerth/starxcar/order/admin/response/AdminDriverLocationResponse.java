package org.mannerth.starxcar.order.admin.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminDriverLocationResponse(BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyM,
                                          BigDecimal speedMps, BigDecimal bearing, LocalDateTime recordedTime,
                                          LocalDateTime receivedTime) {
}
