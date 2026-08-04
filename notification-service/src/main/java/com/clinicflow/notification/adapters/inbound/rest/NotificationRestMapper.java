package com.clinicflow.notification.adapters.inbound.rest;

import com.clinicflow.notification.adapters.inbound.rest.dto.NotificationResponse;
import com.clinicflow.notification.application.mapping.MapperConfiguration;
import com.clinicflow.notification.application.queries.GetNotificationQuery;
import com.clinicflow.notification.application.results.NotificationResult;
import com.clinicflow.notification.domain.NotificationId;
import org.mapstruct.Mapper;

import java.util.UUID;

@Mapper(config = MapperConfiguration.class)
public abstract class NotificationRestMapper {
    public GetNotificationQuery toQuery(UUID id) {
        return new GetNotificationQuery(new NotificationId(id));
    }

    public abstract NotificationResponse toResponse(NotificationResult result);
}
