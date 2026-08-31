package org.mannerth.starxcar.infrastructure.image.model;

import com.fasterxml.jackson.annotation.JsonProperty;






public record ImageUploadData(String key, String name, @JsonProperty("origin_name") String originName,
                              String pathname, Float size, String mimetype, String extension,
                              ImageLinks links) {
    public record ImageLinks(String url, String html, String bbcode, String markdown,
                             @JsonProperty("markdown_with_link") String markdownWithLink,
                             @JsonProperty("thumbnail_url") String thumbnailUrl) {
    }
}