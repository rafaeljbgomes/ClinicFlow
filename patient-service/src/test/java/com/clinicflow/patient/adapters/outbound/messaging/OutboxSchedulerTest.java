package com.clinicflow.patient.adapters.outbound.messaging;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
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
    void dispatchStopsWhenNoEventIsDue() {
        // Arrange
        when(dispatcher.dispatchNext()).thenReturn(true, false);

        // Act
        scheduler.dispatch();

        // Assert
        verify(dispatcher, times(2)).dispatchNext();
    }

    @Test
    void dispatchDrainsAtMostTheConfiguredBatch() {
        // Arrange
        when(dispatcher.dispatchNext()).thenReturn(true);

        // Act
        scheduler.dispatch();

        // Assert
        verify(dispatcher, times(3)).dispatchNext();
    }

    @Test
    void dispatchRefreshesBacklogMetrics() {
        // Arrange
        when(dispatcher.dispatchNext()).thenReturn(false);

        // Act
        scheduler.dispatch();

        // Assert
        verify(metrics).refreshBacklog();
    }

    @Test
    void dispatchContainsCycleFailures() {
        // Arrange
        when(dispatcher.dispatchNext()).thenThrow(new IllegalStateException("database unavailable"));

        // Act
        Throwable thrown = catchThrowable(scheduler::dispatch);

        // Assert
        assertThat(thrown).isNull();
    }

    @Test
    void cleanupPurgesPublishedEventsAndRefreshesBacklogMetrics() {
        // Act
        scheduler.cleanup();

        // Assert
        verify(dispatcher).cleanupPublished();
        verify(metrics).refreshBacklog();
    }

    @Test
    void cleanupContainsCycleFailures() {
        // Arrange
        when(dispatcher.cleanupPublished()).thenThrow(new IllegalStateException("database unavailable"));

        // Act
        Throwable thrown = catchThrowable(scheduler::cleanup);

        // Assert
        assertThat(thrown).isNull();
    }
}
