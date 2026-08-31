package org.mannerth.starxcar.web;

import org.slf4j.MDC;




public record ApiResponse<T>(int code, String message, String traceId, T data) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", currentTraceId(), data);
    }

    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<>(code, message, currentTraceId(), null);
    }

    private static String currentTraceId() {
        return MDC.get("traceId");
    }
}