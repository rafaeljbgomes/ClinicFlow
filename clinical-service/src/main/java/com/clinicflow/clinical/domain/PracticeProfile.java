package com.clinicflow.clinical.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

public final class PracticeProfile {
    private final PsychologistId psychologistId;
    private String professionalRegistration;
    private ZoneId timezone;
    private int defaultSessionDurationMinutes;
    private String primaryLocation;
    private boolean telehealthEnabled;
    private final Instant createdAt;
    private Instant updatedAt;

    private PracticeProfile(PsychologistId psychologistId, String professionalRegistration, ZoneId timezone,
                            int defaultSessionDurationMinutes, String primaryLocation, boolean telehealthEnabled,
                            Instant createdAt, Instant updatedAt) {
        if (psychologistId == null || timezone == null || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("Practice profile required fields are missing");
        }
        requireDuration(defaultSessionDurationMinutes);
        this.psychologistId = psychologistId;
        this.professionalRegistration = TextFields.optional(professionalRegistration, 80,
                "Professional registration is invalid");
        this.timezone = timezone;
        this.defaultSessionDurationMinutes = defaultSessionDurationMinutes;
        this.primaryLocation = TextFields.optional(primaryLocation, 160, "Primary location is invalid");
        this.telehealthEnabled = telehealthEnabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PracticeProfile create(PsychologistId psychologistId, String professionalRegistration,
                                         ZoneId timezone, int defaultSessionDurationMinutes,
                                         String primaryLocation, boolean telehealthEnabled, Instant now) {
        return new PracticeProfile(psychologistId, professionalRegistration, timezone,
                defaultSessionDurationMinutes, primaryLocation, telehealthEnabled, now, now);
    }

    public static PracticeProfile rehydrate(PsychologistId psychologistId, String professionalRegistration,
                                            ZoneId timezone, int defaultSessionDurationMinutes,
                                            String primaryLocation, boolean telehealthEnabled,
                                            Instant createdAt, Instant updatedAt) {
        return new PracticeProfile(psychologistId, professionalRegistration, timezone,
                defaultSessionDurationMinutes, primaryLocation, telehealthEnabled, createdAt, updatedAt);
    }

    public PracticeProfile update(String professionalRegistration, ZoneId timezone, int defaultSessionDurationMinutes,
                                  String primaryLocation, boolean telehealthEnabled, Instant now) {
        if (timezone == null || now == null || now.isBefore(createdAt)) {
            throw new IllegalArgumentException("Practice profile update is invalid");
        }
        requireDuration(defaultSessionDurationMinutes);
        this.professionalRegistration = TextFields.optional(professionalRegistration, 80,
                "Professional registration is invalid");
        this.timezone = timezone;
        this.defaultSessionDurationMinutes = defaultSessionDurationMinutes;
        this.primaryLocation = TextFields.optional(primaryLocation, 160, "Primary location is invalid");
        this.telehealthEnabled = telehealthEnabled;
        this.updatedAt = now;
        return this;
    }

    private static void requireDuration(int value) {
        if (value < 15 || value > 240) {
            throw new IllegalArgumentException("Default session duration is invalid");
        }
    }

    public PsychologistId psychologistId() { return new PsychologistId(psychologistId.value()); }
    public String professionalRegistration() { return professionalRegistration; }
    public ZoneId timezone() { return timezone; }
    public int defaultSessionDurationMinutes() { return defaultSessionDurationMinutes; }
    public String primaryLocation() { return primaryLocation; }
    public boolean telehealthEnabled() { return telehealthEnabled; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PracticeProfile profile
                && psychologistId.equals(profile.psychologistId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(psychologistId);
    }
}
