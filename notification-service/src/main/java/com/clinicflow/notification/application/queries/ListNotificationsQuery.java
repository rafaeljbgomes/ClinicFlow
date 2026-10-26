package com.clinicflow.notification.application.queries;

import com.clinicflow.notification.domain.NotificationStatus;

import java.util.Set;

// An empty status set means every status.
public record ListNotificationsQuery(Set<NotificationStatus> statuses, PageQuery page) {
    public ListNotificationsQuery {
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
        if (page == null) {
            throw new IllegalArgumentException("page is required");
        }
    }
}
