package org.mannerth.starxcar.order.complaint.application;

import org.mannerth.starxcar.order.complaint.response.ComplaintResponse;
import org.mannerth.starxcar.order.complaint.response.ComplaintPageResponse;
import java.math.BigDecimal;

public interface ComplaintService {
    ComplaintResponse create(Long userId, String orderNo, String type, String content);
    ComplaintResponse resolve(Long adminId, String complaintNo, String resolutionType, BigDecimal refundAmount,
                              String content);
    ComplaintPageResponse myComplaints(Long userId, long page, long size);
    ComplaintPageResponse adminComplaints(String status, long page, long size);
    ComplaintResponse adminDetail(String complaintNo);
}
