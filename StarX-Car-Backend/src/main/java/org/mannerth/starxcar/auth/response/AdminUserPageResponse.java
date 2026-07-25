package org.mannerth.starxcar.auth.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "后台用户分页结果")
public record AdminUserPageResponse(
        @Schema(description = "当前页，从 1 开始", example = "1") long page,
        @Schema(description = "每页数量", example = "10") long size,
        @Schema(description = "匹配的用户总数", example = "25") long total,
        @Schema(description = "当前页用户列表") List<UserResponse> records) {
}
