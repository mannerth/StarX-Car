package org.mannerth.starxcar.infrastructure.image;

import org.mannerth.starxcar.config.ImageUploadProperties;
import org.mannerth.starxcar.infrastructure.image.model.ImageApiResponse;
import org.mannerth.starxcar.infrastructure.image.model.ImageTokenData;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;






@Service
public class ImageTokenService {
    private final RestClient imageRestClient;
    private final ImageUploadProperties properties;
    private volatile String token;

    public ImageTokenService(@Qualifier("imageRestClient") RestClient imageRestClient, ImageUploadProperties properties) {
        this.imageRestClient = imageRestClient;
        this.properties = properties;
    }

    public String getToken() {
        String currentToken = token;
        if (currentToken == null || currentToken.isBlank()) {
            synchronized (this) {
                if (token == null || token.isBlank()) {
                    token = requestToken();
                }
                currentToken = token;
            }
        }
        return currentToken;
    }

    public void clearToken() {
        token = null;
    }

    private String requestToken() {
        if (properties.getEmail().isBlank() || properties.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "图片服务账号尚未配置");
        }
        try {
            ImageApiResponse<ImageTokenData> response = imageRestClient.post().uri("/tokens")
                    .body(Map.of("email", properties.getEmail(), "password", properties.getPassword()))
                    .retrieve().body(new ParameterizedTypeReference<>() {});
            if (response == null || !response.status() || response.data() == null || response.data().token() == null
                    || response.data().token().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "图片服务未返回有效授权令牌");
            }
            return response.data().token();
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "图片服务授权失败");
        }
    }
}