package org.mannerth.starxcar.auth.application;

import org.mannerth.starxcar.auth.request.AdminUserUpdateRequest;
import org.mannerth.starxcar.auth.response.AdminUserPageResponse;
import org.mannerth.starxcar.auth.response.UserResponse;
import org.mannerth.starxcar.constant.UserType;






public interface AdminUserService {
    AdminUserPageResponse list(long page, long size, String keyword, UserType type);

    UserResponse get(Long userId);

    UserResponse update(Long userId, AdminUserUpdateRequest request);

    void resetPassword(Long userId);

    void delete(Long userId, Long operatorId);
}