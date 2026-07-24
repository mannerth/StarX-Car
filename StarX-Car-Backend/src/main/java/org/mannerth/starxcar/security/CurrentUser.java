package org.mannerth.starxcar.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class CurrentUser {
    private CurrentUser() {
    }

    public static Long id(String principal) {
        try {
            return Long.valueOf(principal);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无效的认证主体");
        }
    }
}
