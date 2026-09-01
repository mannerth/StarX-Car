package org.mannerth.starxcar.order.complaint.application;

import org.mannerth.starxcar.order.application.OrderAccessService;
import org.mannerth.starxcar.order.complaint.domain.Complaint;
import org.mannerth.starxcar.order.complaint.infrastructure.ComplaintMapper;
import org.mannerth.starxcar.order.complaint.response.ComplaintResponse;
import org.mannerth.starxcar.order.complaint.response.ComplaintPageResponse;
import org.mannerth.starxcar.order.domain.RideOrder;
import org.mannerth.starxcar.order.infrastructure.RideOrderMapper;
import org.mannerth.starxcar.order.payment.application.BalanceService;
import org.mannerth.starxcar.order.payment.domain.OrderPayment;
import org.mannerth.starxcar.order.payment.infrastructure.OrderPaymentMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ComplaintServiceImpl implements ComplaintService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final OrderAccessService accessService; private final ComplaintMapper complaintMapper;
    private final OrderPaymentMapper paymentMapper; private final BalanceService balanceService; private final Clock clock;
    private final RideOrderMapper rideOrderMapper;
    public ComplaintServiceImpl(OrderAccessService accessService, ComplaintMapper complaintMapper,
                                OrderPaymentMapper paymentMapper, BalanceService balanceService, Clock clock,
                                RideOrderMapper rideOrderMapper) {
        this.accessService = accessService; this.complaintMapper = complaintMapper; this.paymentMapper = paymentMapper;
        this.balanceService = balanceService; this.clock = clock; this.rideOrderMapper = rideOrderMapper;
    }
    @Override @Transactional
    public ComplaintResponse create(Long userId, String orderNo, String type, String content) {
        RideOrder order = accessService.requireByOrderNo(orderNo);
        if (!userId.equals(order.getPassengerId()) && !userId.equals(order.getDriverId())) throw notFound();
        if (order.getCompletedTime() == null || order.getCompletedTime().plusDays(7).isBefore(now())) throw conflict("仅允许订单结束后七天内投诉");
        Complaint complaint = new Complaint(); complaint.setComplaintNo("C" + UUID.randomUUID().toString().replace("-", ""));
        complaint.setOrderId(order.getId()); complaint.setComplainantUserId(userId); complaint.setComplaintType(type.trim());
        complaint.setContent(content.trim()); complaint.setStatus("OPEN"); complaint.setRefundAmount(BigDecimal.ZERO); complaint.setCreatedTime(now());
        if (complaintMapper.insert(complaint) != 1) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "创建投诉失败");
        return response(complaint);
    }
    @Override @Transactional
    public ComplaintResponse resolve(Long adminId, String complaintNo, String resolutionType, BigDecimal refundAmount,
                                     String content) {
        Complaint complaint = complaintMapper.selectByComplaintNoForUpdate(complaintNo);
        if (complaint == null) throw notFound(); if (!"OPEN".equals(complaint.getStatus()) && !"PROCESSING".equals(complaint.getStatus())) throw conflict("投诉已处理");
        validateResolution(resolutionType, refundAmount);
        String normalizedContent = required(content, "处理说明");
        LocalDateTime time = now();
        if (refundAmount.signum() > 0) {
            OrderPayment payment = paymentMapper.selectByOrderIdForUpdate(complaint.getOrderId());
            if (payment == null) throw conflict("订单尚未支付，不能退款");
            BigDecimal refundable = payment.getPaidAmount().subtract(payment.getRefundedAmount());
            if ("FULL_REFUND".equals(resolutionType) && refundAmount.compareTo(refundable) != 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "全额退款必须退还全部可退金额");
            if ("PARTIAL_REFUND".equals(resolutionType) && refundAmount.compareTo(refundable) >= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部分退款金额必须小于可退金额");
            BigDecimal next = payment.getRefundedAmount().add(refundAmount);
            if (next.compareTo(payment.getPaidAmount()) > 0) throw conflict("退款金额超过订单实付金额");
            String status = next.compareTo(payment.getPaidAmount()) == 0 ? "REFUNDED" : "PARTIALLY_REFUNDED";
            if (paymentMapper.updateRefund(payment.getId(), payment.getVersion(), next, status, "REFUNDED".equals(status) ? time : null) != 1) throw conflict("支付记录已变化");
            balanceService.change(payment.getPayerUserId(), complaint.getOrderId(), complaint.getId(), refundAmount, "REFUND", complaintNo + ":passenger:" + next, "投诉退款", false);
            balanceService.change(payment.getPayeeUserId(), complaint.getOrderId(), complaint.getId(), refundAmount.negate(), "REFUND", complaintNo + ":driver:" + next, "投诉退款回冲", false);
            rideOrderMapper.updatePaymentStatus(complaint.getOrderId(), status);
        }
        if (complaintMapper.resolve(complaint.getId(), resolutionType, refundAmount, adminId, time) != 1) throw conflict("投诉状态已变化");
        if (complaintMapper.insertHandleLog(complaint.getId(), adminId, resolutionType, normalizedContent,
                refundAmount, time) != 1) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "保存投诉处理说明失败");
        }
        complaint.setStatus("RESOLVED"); complaint.setResolutionType(resolutionType); complaint.setRefundAmount(refundAmount); complaint.setResolvedTime(time); return response(complaint);
    }
    @Override
    public ComplaintPageResponse myComplaints(Long userId, long page, long size) {
        return page(complaintMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<Complaint>()
                .eq(Complaint::getComplainantUserId, userId).orderByDesc(Complaint::getCreatedTime)));
    }

    @Override
    public ComplaintPageResponse adminComplaints(String status, long page, long size) {
        LambdaQueryWrapper<Complaint> query = new LambdaQueryWrapper<Complaint>().orderByDesc(Complaint::getCreatedTime);
        if (status != null && !status.isBlank()) query.eq(Complaint::getStatus, status.trim().toUpperCase());
        return page(complaintMapper.selectPage(new Page<>(page, size), query));
    }

    @Override
    public ComplaintResponse adminDetail(String complaintNo) {
        Complaint complaint = complaintMapper.selectOne(new LambdaQueryWrapper<Complaint>().eq(Complaint::getComplaintNo, complaintNo));
        if (complaint == null) throw notFound();
        return response(complaint);
    }

    private ComplaintPageResponse page(Page<Complaint> result) {
        List<ComplaintResponse> records = result.getRecords().stream().map(this::response).toList();
        return new ComplaintPageResponse(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }
    private void validateResolution(String type, BigDecimal amount) {
        if (!("APOLOGY".equals(type) || "PARTIAL_REFUND".equals(type) || "FULL_REFUND".equals(type) || "REJECT".equals(type))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的处理方式");
        if (("APOLOGY".equals(type) || "REJECT".equals(type)) && amount.signum() != 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅道歉或驳回不能退款");
        if ("PARTIAL_REFUND".equals(type) && amount.signum() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部分退款金额必须大于零");
        if ("FULL_REFUND".equals(type) && amount.signum() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "全额退款金额必须大于零");
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(Instant.now(clock), ZONE).withNano(0); }
    private ComplaintResponse response(Complaint c) { RideOrder order = rideOrderMapper.selectById(c.getOrderId()); if (order == null) throw notFound(); String resolutionContent = c.getResolutionType() == null ? null : complaintMapper.selectLatestHandleContent(c.getId()); return new ComplaintResponse(c.getComplaintNo(), order.getOrderNo(), c.getComplaintType(), c.getContent(), c.getStatus(), c.getResolutionType(), c.getRefundAmount(), resolutionContent, c.getCreatedTime(), c.getResolvedTime()); }
    private String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + "不能为空");
        }
        return value.trim();
    }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "投诉不存在或无权访问"); }
    private ResponseStatusException conflict(String m) { return new ResponseStatusException(HttpStatus.CONFLICT, m); }
}
