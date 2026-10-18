package org.mannerth.starxcar.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.mockito.Mockito.*;

class RealtimeChangesTest {
    @Test void productionPointcutObservesVoidWorkflowsButNotFailuresOrEmptyBatches() {
        RealtimeHandler handler = mock(RealtimeHandler.class);
        @SuppressWarnings("unchecked") ObjectProvider<RealtimeHandler> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(handler);
        var factory = new org.springframework.aop.aspectj.annotation.AspectJProxyFactory(
                new org.mannerth.starxcar.order.application.RealtimeWorkflowFixture());
        factory.addAspect(new RealtimeChanges(provider));
        org.mannerth.starxcar.order.application.RealtimeWorkflowFixture workflow = factory.getProxy();
        workflow.emptyBatch();
        org.assertj.core.api.Assertions.assertThatThrownBy(workflow::fail).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(handler);
        workflow.mutate();
        verify(handler).ordersChanged();
    }

    @Test void rolledBackSynchronizationDoesNotPublish() {
        RealtimeHandler handler = mock(RealtimeHandler.class);
        @SuppressWarnings("unchecked") ObjectProvider<RealtimeHandler> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(handler);
        TransactionSynchronizationManager.initSynchronization();
        try {
            new RealtimeChanges(provider).orderChanged(null);
            TransactionSynchronizationManager.getSynchronizations().forEach(sync ->
                    sync.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(handler);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test void publishesOnlyAfterCommitAndIgnoresEmptySchedulerBatches() {
        RealtimeHandler handler = mock(RealtimeHandler.class);
        @SuppressWarnings("unchecked") ObjectProvider<RealtimeHandler> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(handler);
        RealtimeChanges changes = new RealtimeChanges(provider);
        changes.orderChanged(0);
        verifyNoInteractions(handler);
        TransactionSynchronizationManager.initSynchronization();
        try {
            changes.orderChanged(null);
            changes.locationChanged(99L);
            verifyNoInteractions(handler);
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
            verify(handler).ordersChanged(); verify(handler).locationChanged(99L);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
}
