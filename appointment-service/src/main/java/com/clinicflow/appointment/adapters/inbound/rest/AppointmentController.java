package com.clinicflow.appointment.adapters.inbound.rest;

import com.clinicflow.appointment.adapters.inbound.rest.dto.AppointmentResponse;
import com.clinicflow.appointment.adapters.inbound.rest.dto.CancelAppointmentRequest;
import com.clinicflow.appointment.adapters.inbound.rest.dto.RescheduleAppointmentRequest;
import com.clinicflow.appointment.adapters.inbound.rest.dto.ScheduleAppointmentRequest;
import com.clinicflow.appointment.application.AppointmentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    @GetMapping
    public List<AppointmentResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return appointmentService.listForPsychologist(mapper.toListQuery(userId(jwt))).stream()
                .map(mapper::toResponse).toList();
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
        return UUID.fromString(jwt.getClaimAsString("userId"));
    }
}
