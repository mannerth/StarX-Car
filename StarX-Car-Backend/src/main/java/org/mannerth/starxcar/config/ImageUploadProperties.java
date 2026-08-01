package org.mannerth.starxcar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "image-service")
public class ImageUploadProperties {
    private String baseUrl = "https://image.foofish.work/api/v1";
    private String email = "";
    private String password = "";
    private Integer strategyId;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Integer getStrategyId() { return strategyId; }
    public void setStrategyId(Integer strategyId) { this.strategyId = strategyId; }
}
