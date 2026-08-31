package org.mannerth.starxcar.infrastructure.image;

import org.mannerth.starxcar.config.ImageUploadProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;






@Configuration
@EnableConfigurationProperties(ImageUploadProperties.class)
public class ImageConfig {

    @Bean
    RestClient imageRestClient(RestClient.Builder builder, ImageUploadProperties properties) {
        return builder.baseUrl(properties.getBaseUrl())
                .defaultHeader("Accept", "application/json")
                .build();
    }
}