package org.mannerth.starxcar.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationDispatchScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReservationDispatchScheduler.class);

    private final ReservationDispatchService reservationDispatchService;

    public ReservationDispatchScheduler(ReservationDispatchService reservationDispatchService) {
        this.reservationDispatchService = reservationDispatchService;
    }

    @Scheduled(fixedDelayString = "${reservation.dispatch-interval-ms}")
    public void run() {
        int dispatched = reservationDispatchService.dispatchDueReservations();
        if (dispatched > 0) {
            log.info("预约调度：{} 笔预约订单进入可抢单 PENDING 状态", dispatched);
        }
        int cancelled = reservationDispatchService.expireStaleReservations();
        if (cancelled > 0) {
            log.info("预约调度：{} 笔超时未接单预约订单已被系统取消", cancelled);
        }
    }
}
