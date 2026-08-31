package org.mannerth.starxcar.order.application;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.stereotype.Component;






@Component
public class OrderNumberGenerator {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
            .withZone(ZoneId.of("Asia/Shanghai"));

    private final Clock clock;
    private long lastTimestamp;
    private int sequence;

    public OrderNumberGenerator(Clock clock) {
        this.clock = clock;
    }

    public synchronized String next() {
        long timestamp = Math.max(clock.millis(), lastTimestamp);
        sequence = timestamp == lastTimestamp ? sequence + 1 : 0;
        if (sequence > 99_999) {
            throw new IllegalStateException("订单号生成频率超过单毫秒限制");
        }
        lastTimestamp = timestamp;
        return "O" + FORMATTER.format(Instant.ofEpochMilli(timestamp)) + String.format("%05d", sequence);
    }
}