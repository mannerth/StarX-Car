package org.mannerth.starxcar.order.complaint.application;

import org.mannerth.starxcar.order.application.OrderAccessService;
import org.mannerth.starxcar.order.complaint.domain.Complaint;
import org.mannerth.starxcar.order.complaint.infrastructure.ComplaintMapper;
import org.mannerth.starxcar.order.complaint.response.ComplaintPageResponse;
import org.mannerth.starxcar.order.complaint.response.ComplaintResponse;
import org.mannerth.starxcar.order.domain.RideOrder;
import org.mannerth.starxcar.order.infrastructure.RideOrderMapper;
import org.mannerth.starxcar.order.payment.application.BalanceService;
import org.mannerth.starxcar.order.payment.infrastructure.OrderPaymentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ComplaintServiceImplTests {
    private static final Instant NOW_INSTANT = Instant.parse("2026-09-01T06:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, ZoneId.of("Asia/Shanghai"));

    private ComplaintMapper complaintMapper;
    private RideOrderMapper rideOrderMapper;
    private ComplaintServiceImpl service;

    @BeforeEach
    void setUp() {
        complaintMapper = mock(ComplaintMapper.class);
        rideOrderMapper = mock(RideOrderMapper.class);
        service = new ComplaintServiceImpl(mock(OrderAccessService.class), complaintMapper,
                mock(OrderPaymentMapper.class), mock(BalanceService.class),
                Clock.fixed(NOW_INSTANT, ZoneId.of("Asia/Shanghai")), rideOrderMapper);
    }

    @Test
    void resolvingComplaintPersistsTrimmedAdminReplyAndReturnsIt() {
        Complaint complaint = complaint("OPEN", null);
        given(complaintMapper.selectByComplaintNoForUpdate("C1")).willReturn(complaint);
        given(complaintMapper.resolve(20L, "APOLOGY", BigDecimal.ZERO, 9L, NOW)).willReturn(1);
        given(complaintMapper.insertHandleLog(20L, 9L, "APOLOGY", "已核实并向您致歉",
                BigDecimal.ZERO, NOW)).willReturn(1);
        given(complaintMapper.selectLatestHandleContent(20L)).willReturn("已核实并向您致歉");
        given(rideOrderMapper.selectById(10L)).willReturn(order());

        ComplaintResponse result = service.resolve(9L, "C1", "APOLOGY", BigDecimal.ZERO,
                "  已核实并向您致歉  ");

        assertThat(result.status()).isEqualTo("RESOLVED");
        assertThat(result.resolutionContent()).isEqualTo("已核实并向您致歉");
        assertThat(result.resolvedTime()).isEqualTo(NOW);
        verify(complaintMapper).insertHandleLog(20L, 9L, "APOLOGY", "已核实并向您致歉",
                BigDecimal.ZERO, NOW);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void myComplaintsReturnsResolutionProgressAndAdminReply() {
        Complaint complaint = complaint("RESOLVED", "PARTIAL_REFUND");
        complaint.setRefundAmount(new BigDecimal("5.00"));
        complaint.setResolvedTime(NOW);
        Page<Complaint> resultPage = new Page<>(1, 20);
        resultPage.setTotal(1);
        resultPage.setRecords(List.of(complaint));
        given(complaintMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .willReturn(resultPage);
        given(complaintMapper.selectLatestHandleContent(20L)).willReturn("已退回部分费用");
        given(rideOrderMapper.selectById(10L)).willReturn(order());

        ComplaintPageResponse result = service.myComplaints(1L, 1, 20);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).hasSize(1);
        assertThat(result.records().getFirst()).satisfies(item -> {
            assertThat(item.orderNo()).isEqualTo("O1");
            assertThat(item.status()).isEqualTo("RESOLVED");
            assertThat(item.resolutionType()).isEqualTo("PARTIAL_REFUND");
            assertThat(item.refundAmount()).isEqualByComparingTo("5.00");
            assertThat(item.resolutionContent()).isEqualTo("已退回部分费用");
        });
    }

    @Test
    void resolveIsTransactional() throws Exception {
        assertThat(ComplaintServiceImpl.class.getMethod("resolve", Long.class, String.class, String.class,
                BigDecimal.class, String.class).isAnnotationPresent(Transactional.class)).isTrue();
    }

    private Complaint complaint(String status, String resolutionType) {
        Complaint complaint = new Complaint();
        complaint.setId(20L);
        complaint.setComplaintNo("C1");
        complaint.setOrderId(10L);
        complaint.setComplainantUserId(1L);
        complaint.setComplaintType("ORDER_SERVICE");
        complaint.setContent("司机服务问题");
        complaint.setStatus(status);
        complaint.setResolutionType(resolutionType);
        complaint.setRefundAmount(BigDecimal.ZERO);
        complaint.setCreatedTime(NOW.minusDays(1));
        return complaint;
    }

    private RideOrder order() {
        RideOrder order = new RideOrder();
        order.setId(10L);
        order.setOrderNo("O1");
        return order;
    }
}
