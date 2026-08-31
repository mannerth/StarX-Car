package org.mannerth.starxcar.infrastructure.image.model;







public record ImageApiResponse<T>(boolean status, String message, T data) {
}