package org.mannerth.starxcar.driver.application;

import org.mannerth.starxcar.driver.domain.DriverApprovalDecision;
import org.mannerth.starxcar.driver.response.AdminDriverPageResponse;
import org.mannerth.starxcar.driver.response.AdminDriverResponse;






public interface AdminDriverService {
    AdminDriverPageResponse list(long page, long size, String keyword, String approvalStatus);

    AdminDriverResponse review(Long driverId, DriverApprovalDecision decision);
}