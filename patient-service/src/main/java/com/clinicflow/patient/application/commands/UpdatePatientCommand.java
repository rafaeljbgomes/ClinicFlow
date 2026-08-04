package com.clinicflow.patient.application.commands;

import com.clinicflow.patient.domain.ConsentStatus;
import com.clinicflow.patient.domain.BirthDate;
import com.clinicflow.patient.domain.ContactPreference;
import com.clinicflow.patient.domain.EmergencyContact;
import com.clinicflow.patient.domain.FullName;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PhoneNumber;
import com.clinicflow.patient.domain.PreferredName;
import com.clinicflow.patient.domain.PsychologistId;

public record UpdatePatientCommand(PsychologistId psychologistId, PatientId patientId, FullName fullName,
                                   PreferredName preferredName, BirthDate birthDate, PhoneNumber phone,
                                   EmergencyContact emergencyContact, ContactPreference contactPreference,
                                   ConsentStatus consentStatus) {
}
