package org.mannerth.starxcar.infra.image.model;

public record ImageApiResponse<T>(boolean status, String message, T data) {
}
