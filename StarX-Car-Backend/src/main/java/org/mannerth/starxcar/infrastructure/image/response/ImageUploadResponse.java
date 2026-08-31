package org.mannerth.starxcar.infrastructure.image.response;

import org.mannerth.starxcar.infrastructure.image.model.ImageUploadData;
import io.swagger.v3.oas.annotations.media.Schema;






@Schema(description = "成功上传后的图片访问地址")
public record ImageUploadResponse(
        @Schema(description = "图床图片唯一密钥", example = "a1b2c3d4") String key,
        @Schema(description = "原始文件名", example = "avatar.png") String originName,
        @Schema(description = "图片公开访问地址", example = "https://image.foofish.work/i/a1b2c3d4.png") String url,
        @Schema(description = "缩略图访问地址") String thumbnailUrl) {
    public static ImageUploadResponse from(ImageUploadData data) {
        return new ImageUploadResponse(data.key(), data.originName(), data.links().url(), data.links().thumbnailUrl());
    }
}