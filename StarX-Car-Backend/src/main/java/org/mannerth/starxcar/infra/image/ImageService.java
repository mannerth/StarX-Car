package org.mannerth.starxcar.infra.image;

import org.mannerth.starxcar.config.ImageUploadProperties;
import org.mannerth.starxcar.infra.image.model.ImageApiResponse;
import org.mannerth.starxcar.infra.image.model.ImageUploadData;
import org.mannerth.starxcar.infra.image.response.ImageUploadResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@Service
public class ImageService {
    private final RestClient imageRestClient;
    private final ImageTokenService tokenService;
    private final ImageUploadProperties properties;

    public ImageService(@Qualifier("imageRestClient") RestClient imageRestClient, ImageTokenService tokenService,
                        ImageUploadProperties properties) {
        this.imageRestClient = imageRestClient;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    public ImageUploadResponse upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传文件不能为空");
        }
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持上传图片文件");
        }
        try {
            return doUpload(file, tokenService.getToken());
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() != HttpStatus.UNAUTHORIZED) {
                throw exception;
            }
            tokenService.clearToken();
            return doUpload(file, tokenService.getToken());
        }
    }

    private ImageUploadResponse doUpload(MultipartFile file, String token) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
                }
            });
            if (properties.getStrategyId() != null) {
                body.add("strategy_id", properties.getStrategyId());
            }
            ImageApiResponse<ImageUploadData> response = imageRestClient.post().uri("/upload")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body).retrieve().body(new ParameterizedTypeReference<>() {});
            if (response == null || !response.status() || response.data() == null || response.data().links() == null
                    || response.data().links().url() == null || response.data().links().url().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "图片服务未返回图片地址");
            }
            return ImageUploadResponse.from(response.data());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (org.springframework.web.client.RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "图片服务授权已失效");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "图片服务上传失败");
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "读取上传文件失败");
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "图片服务上传失败");
        }
    }
}
