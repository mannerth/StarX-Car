package org.mannerth.starxcar.infrastructure.image.api;

import org.mannerth.starxcar.infrastructure.image.ImageService;
import org.mannerth.starxcar.infrastructure.image.response.ImageUploadResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;






@RestController
@RequestMapping("/api/image")
@Tag(name = "图片", description = "通过受控图床服务上传业务图片")
@SecurityRequirement(name = "bearerAuth")
public class ImageController {
    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传图片", description = "任意已登录用户上传单张图片到配置的图床服务；服务端使用图床账号授权，成功后返回公开访问地址。空文件、非图片文件、图片服务未配置或不可用时请求失败。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "上传成功"),
            @ApiResponse(responseCode = "400", description = "文件为空、类型不正确或文件无法读取"),
            @ApiResponse(responseCode = "401", description = "未登录或访问令牌无效"),
            @ApiResponse(responseCode = "502", description = "图床服务授权、上传或响应异常"),
            @ApiResponse(responseCode = "503", description = "未配置图床账号")
    })
    public ImageUploadResponse upload(
            @RequestPart("file") @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "object", requiredProperties = "file"))) MultipartFile file) {
        return imageService.upload(file);
    }
}