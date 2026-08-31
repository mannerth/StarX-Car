package org.mannerth.starxcar.auth.response;







public record TokenResponse(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
}