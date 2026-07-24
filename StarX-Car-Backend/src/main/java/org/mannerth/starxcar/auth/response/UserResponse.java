package org.mannerth.starxcar.auth.response;

import org.mannerth.starxcar.auth.User;
import org.mannerth.starxcar.constant.GenderEnum;
import org.mannerth.starxcar.constant.UserType;

import java.time.LocalDateTime;

public record UserResponse(Long id, String username, String email, GenderEnum gender, UserType type,
                           LocalDateTime createTime, LocalDateTime lastLoginTime) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getGender(), user.getType(),
                user.getCreateTime(), user.getLastLoginTime());
    }
}
