package org.mannerth.starxcar.order.application;

import org.springframework.transaction.annotation.Transactional;

/** In-memory workflow for exercising the production event pointcut without any datasource. */
public class RealtimeWorkflowFixture {
    @Transactional public void mutate() { }
    @Transactional public int emptyBatch() { return 0; }
    @Transactional public void fail() { throw new IllegalStateException("rolled back"); }
}
