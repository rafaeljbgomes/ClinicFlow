package com.clinicflow.appointment.adapters.outbound.messaging;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxSchedulerTest {
    private final OutboxEventDispatcher dispatcher = mock(OutboxEventDispatcher.class);
    private final OutboxMetrics metrics = mock(OutboxMetrics.class);
    private final OutboxProperties properties = new OutboxProperties(
            true, Duration.ofSeconds(1), 3, Duration.ofSeconds(5),
            Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofHours(1), Duration.ofDays(7));
    private final OutboxScheduler scheduler = new OutboxScheduler(dispatcher, properties, metrics);

    @Test
    void drainsUpToConfiguredBatchAndRefreshesMetrics() {
        when(dispatcher.dispatchNext()).thenReturn(true, true, false);

        scheduler.dispatch();

        verify(dispatcher, times(3)).dispatchNext();
        verify(metrics).refreshBacklog();
    }

    @Test
    void runsCleanupAndContainsCycleFailures() {
        scheduler.cleanup();
        verify(dispatcher).cleanupPublished();
        verify(metrics).refreshBacklog();

        when(dispatcher.dispatchNext()).thenThrow(new IllegalStateException("database unavailable"));
        scheduler.dispatch();
    }
}
