package org.mannerth.starxcar.realtime;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Observe successful existing workflows; no additional persistence or timer-based database reads. */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RealtimeChanges {
    private final org.springframework.beans.factory.ObjectProvider<RealtimeHandler> handler;
    public RealtimeChanges(org.springframework.beans.factory.ObjectProvider<RealtimeHandler> handler) { this.handler = handler; }

    @AfterReturning(pointcut = "execution(public * org.mannerth.starxcar.order..application..*(..)) && @annotation(org.springframework.transaction.annotation.Transactional)", returning = "result")
    public void orderChanged(Object result) {
        if (result instanceof Number count && count.longValue() == 0) return;
        afterCommit(() -> handler.getObject().ordersChanged());
    }

    @AfterReturning("execution(* org.mannerth.starxcar.driver.application.DriverWorkService.reportLocation(..)) && args(driverId,..)")
    public void locationChanged(Long driverId) { afterCommit(() -> handler.getObject().locationChanged(driverId)); }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        } else action.run();
    }
}
