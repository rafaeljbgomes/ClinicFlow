package com.clinicflow.appointment.adapters.inbound.rest;

import com.clinicflow.appointment.adapters.inbound.rest.dto.AppointmentResponse;
import com.clinicflow.appointment.adapters.inbound.rest.dto.CancelAppointmentRequest;
import com.clinicflow.appointment.adapters.inbound.rest.dto.PageResponse;
import com.clinicflow.appointment.adapters.inbound.rest.dto.RescheduleAppointmentRequest;
import com.clinicflow.appointment.adapters.inbound.rest.dto.ScheduleAppointmentRequest;
import com.clinicflow.appointment.application.AppointmentService;
import com.clinicflow.appointment.application.queries.PageQuery;
import com.clinicflow.appointment.application.queries.ScheduleOrder;
import com.clinicflow.appointment.domain.AppointmentStatus;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/appointments")
@PreAuthorize("hasRole('PSYCHOLOGIST')")
public class AppointmentController {
    private static final Logger log = LoggerFactory.getLogger(AppointmentController.class);
    private final AppointmentService appointmentService;
    private final AppointmentRestMapper mapper;

    public AppointmentController(AppointmentService appointmentService, AppointmentRestMapper mapper) {
        this.appointmentService = appointmentService;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> schedule(@AuthenticationPrincipal Jwt jwt,
                                                        @Valid @RequestBody ScheduleAppointmentRequest request) {
        AppointmentResponse appointment = mapper.toResponse(
                appointmentService.schedule(mapper.toCommand(userId(jwt), request)));
        log.info("appointment_scheduled appointmentId={} psychologistId={} patientId={}",
                appointment.id(), appointment.psychologistId(), appointment.patientId());
        return ResponseEntity.status(HttpStatus.CREATED).eTag(EntityTags.of(appointment.version())).body(appointment);
    }

    // from/to bound scheduledAt as [from, to); the dashboard asks for the next seven days this way.
    // order=DESC lists the latest first, for views of past sessions.
    @GetMapping
    public PageResponse<AppointmentResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                  @RequestParam(required = false)
                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                  @RequestParam(required = false)
                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                  @RequestParam(required = false) Set<AppointmentStatus> status,
                                                  @RequestParam(defaultValue = "ASC") ScheduleOrder order,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size) {
        return PageResponse.of(appointmentService.listForPsychologist(
                mapper.toListQuery(userId(jwt), from, to, status, order, page, size)), mapper::toResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return tagged(mapper.toResponse(appointmentService.get(mapper.toGetQuery(userId(jwt), id))));
    }

    @PatchMapping("/{id}/reschedule")
    public ResponseEntity<AppointmentResponse> reschedule(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                          @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                                                          String ifMatch,
                                                          @Valid @RequestBody RescheduleAppointmentRequest request) {
        long expectedVersion = EntityTags.requireIfMatch(ifMatch);
        return tagged(mapper.toResponse(appointmentService.reschedule(
                mapper.toCommand(userId(jwt), id, request, expectedVersion))));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<AppointmentResponse> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                                                      String ifMatch,
                                                      @Valid @RequestBody CancelAppointmentRequest request) {
        long expectedVersion = EntityTags.requireIfMatch(ifMatch);
        return tagged(mapper.toResponse(appointmentService.cancel(
                mapper.toCommand(userId(jwt), id, request, expectedVersion))));
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<AppointmentResponse> complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                        @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                                                        String ifMatch) {
        long expectedVersion = EntityTags.requireIfMatch(ifMatch);
        return tagged(mapper.toResponse(appointmentService.complete(
                mapper.toCompleteCommand(userId(jwt), id, expectedVersion))));
    }

    private static ResponseEntity<AppointmentResponse> tagged(AppointmentResponse appointment) {
        return ResponseEntity.ok().eTag(EntityTags.of(appointment.version())).body(appointment);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
