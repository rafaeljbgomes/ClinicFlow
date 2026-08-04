package com.clinicflow.clinical.infrastructure.bootstrap;

import com.clinicflow.clinical.application.ports.CarePlanRepository;
import com.clinicflow.clinical.application.ports.ClinicalCaseRepository;
import com.clinicflow.clinical.application.ports.PracticeProfileRepository;
import com.clinicflow.clinical.application.ports.SessionRecordRepository;
import com.clinicflow.clinical.domain.AppointmentId;
import com.clinicflow.clinical.domain.AttendanceStatus;
import com.clinicflow.clinical.domain.CareGoal;
import com.clinicflow.clinical.domain.CareGoalId;
import com.clinicflow.clinical.domain.CarePlan;
import com.clinicflow.clinical.domain.CarePlanId;
import com.clinicflow.clinical.domain.ClinicalCase;
import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.ClinicalCaseStatus;
import com.clinicflow.clinical.domain.GoalStatus;
import com.clinicflow.clinical.domain.NoteStatus;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PracticeProfile;
import com.clinicflow.clinical.domain.PsychologistId;
import com.clinicflow.clinical.domain.SessionModality;
import com.clinicflow.clinical.domain.SessionRecord;
import com.clinicflow.clinical.domain.SessionRecordId;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "clinicflow.demo.bootstrap", name = "enabled", havingValue = "true")
public class DemoDataBootstrap implements ApplicationRunner {
    private static final UUID PSYCHOLOGIST_SOFIA = uuid("00000000-0000-4000-8000-000000000011");
    private static final UUID PSYCHOLOGIST_MIGUEL = uuid("00000000-0000-4000-8000-000000000012");

    private final PracticeProfileRepository practiceProfiles;
    private final ClinicalCaseRepository clinicalCases;
    private final CarePlanRepository carePlans;
    private final SessionRecordRepository sessionRecords;
    private final Clock clock;

    @Autowired
    public DemoDataBootstrap(PracticeProfileRepository practiceProfiles, ClinicalCaseRepository clinicalCases,
                             CarePlanRepository carePlans, SessionRecordRepository sessionRecords) {
        this(practiceProfiles, clinicalCases, carePlans, sessionRecords, Clock.systemUTC());
    }

    DemoDataBootstrap(PracticeProfileRepository practiceProfiles, ClinicalCaseRepository clinicalCases,
                      CarePlanRepository carePlans, SessionRecordRepository sessionRecords, Clock clock) {
        this.practiceProfiles = practiceProfiles;
        this.clinicalCases = clinicalCases;
        this.carePlans = carePlans;
        this.sessionRecords = sessionRecords;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        LocalDate today = LocalDate.now(clock);

        seedPracticeProfiles(now);
        List<ClinicalCase> cases = demoCases(now);
        cases.forEach(clinicalCases::save);
        seedCarePlans(cases, today, now);
        seedSessionRecords(cases, now);
    }

    private void seedPracticeProfiles(Instant now) {
        practiceProfiles.save(PracticeProfile.create(
                new PsychologistId(PSYCHOLOGIST_SOFIA),
                "OPP-PT-14523",
                ZoneId.of("Europe/Lisbon"),
                50,
                "Consultorio Avenida da Liberdade, Lisboa",
                true,
                now
        ));
        practiceProfiles.save(PracticeProfile.create(
                new PsychologistId(PSYCHOLOGIST_MIGUEL),
                "OPP-PT-17844",
                ZoneId.of("Europe/Lisbon"),
                60,
                "Clinica Boavista, Porto",
                true,
                now
        ));
    }

    private static List<ClinicalCase> demoCases(Instant now) {
        Instant openedAt = now.minus(60, ChronoUnit.DAYS);
        return List.of(
                clinicalCase(1, PSYCHOLOGIST_SOFIA, patient(1),
                        "Ansiedade generalizada com impacto no sono e desempenho profissional.",
                        ClinicalCaseStatus.ACTIVE, openedAt, null, now),
                clinicalCase(2, PSYCHOLOGIST_SOFIA, patient(2),
                        "Acompanhamento inicial por dificuldades de adaptacao familiar.",
                        ClinicalCaseStatus.INTAKE, openedAt.plus(10, ChronoUnit.DAYS), null, now),
                clinicalCase(3, PSYCHOLOGIST_SOFIA, patient(3),
                        "Sintomatologia depressiva leve, atualmente com acompanhamento pausado.",
                        ClinicalCaseStatus.PAUSED, openedAt.plus(15, ChronoUnit.DAYS), null, now),
                clinicalCase(4, PSYCHOLOGIST_MIGUEL, patient(4),
                        "Gestao de stress associado a contexto laboral exigente.",
                        ClinicalCaseStatus.ACTIVE, openedAt.plus(5, ChronoUnit.DAYS), null, now),
                clinicalCase(5, PSYCHOLOGIST_MIGUEL, patient(5),
                        "Processo concluido apos estabilizacao dos objetivos terapeuticos.",
                        ClinicalCaseStatus.DISCHARGED, openedAt.minus(20, ChronoUnit.DAYS),
                        now.minus(7, ChronoUnit.DAYS), now),
                clinicalCase(6, PSYCHOLOGIST_MIGUEL, patient(6),
                        "Queixas de ansiedade social e evitamento de situacoes de exposicao.",
                        ClinicalCaseStatus.INTAKE, openedAt.plus(20, ChronoUnit.DAYS), null, now)
        );
    }

    private void seedCarePlans(List<ClinicalCase> cases, LocalDate today, Instant now) {
        for (int index = 0; index < cases.size(); index++) {
            ClinicalCase clinicalCase = cases.get(index);
            int suffix = index + 1;
            carePlans.save(CarePlan.create(
                    new CarePlanId(uuid("31000000-0000-4000-8000-00000000000" + suffix)),
                    clinicalCase,
                    therapeuticFocus(suffix),
                    plannedFrequency(suffix),
                    today.plusWeeks(8 + suffix),
                    goalsForCase(suffix, today),
                    now
            ));
        }
    }

    private void seedSessionRecords(List<ClinicalCase> cases, Instant now) {
        sessionRecords.save(SessionRecord.fromCompletedAppointment(
                new SessionRecordId(uuid("33000000-0000-4000-8000-000000000001")),
                cases.get(2).id(),
                new PsychologistId(PSYCHOLOGIST_SOFIA),
                new PatientId(patient(3)),
                new AppointmentId(uuid("20000000-0000-4000-8000-000000000003")),
                now.minus(3, ChronoUnit.DAYS),
                SessionModality.ONLINE,
                50,
                now
        ));
        sessionRecords.save(SessionRecord.rehydrate(
                new SessionRecordId(uuid("33000000-0000-4000-8000-000000000002")),
                cases.get(0).id(),
                new PsychologistId(PSYCHOLOGIST_SOFIA),
                new PatientId(patient(1)),
                null,
                now.minus(10, ChronoUnit.DAYS),
                SessionModality.IN_PERSON,
                50,
                AttendanceStatus.ATTENDED,
                NoteStatus.SIGNED,
                "Paciente identificou principais gatilhos de ansiedade e completou registo semanal.",
                "Ansiedade, sono, estrategias de regulacao emocional",
                "Psicoeducacao, respiracao diafragmatica, planeamento de exposicao gradual",
                "Registar situacoes de ansiedade e resposta fisiologica.",
                "Rever registos e introduzir exposicoes breves.",
                now.minus(10, ChronoUnit.DAYS),
                now
        ));
        sessionRecords.save(SessionRecord.manual(
                new SessionRecordId(uuid("33000000-0000-4000-8000-000000000003")),
                cases.get(3),
                null,
                now.minus(6, ChronoUnit.DAYS),
                SessionModality.ONLINE,
                60,
                AttendanceStatus.NO_SHOW,
                null,
                "Ausencia sem aviso previo; contacto posterior a agendar.",
                null,
                null,
                "Confirmar disponibilidade antes da proxima consulta.",
                now
        ));
        sessionRecords.save(SessionRecord.fromCompletedAppointment(
                new SessionRecordId(uuid("33000000-0000-4000-8000-000000000004")),
                cases.get(5).id(),
                new PsychologistId(PSYCHOLOGIST_MIGUEL),
                new PatientId(patient(6)),
                new AppointmentId(uuid("20000000-0000-4000-8000-000000000006")),
                now.minus(1, ChronoUnit.DAYS),
                SessionModality.IN_PERSON,
                60,
                now
        ));
        sessionRecords.save(SessionRecord.rehydrate(
                new SessionRecordId(uuid("33000000-0000-4000-8000-000000000005")),
                cases.get(1).id(),
                new PsychologistId(PSYCHOLOGIST_SOFIA),
                new PatientId(patient(2)),
                null,
                now.minus(2, ChronoUnit.DAYS),
                SessionModality.IN_PERSON,
                0,
                AttendanceStatus.CANCELLED_LATE,
                NoteStatus.DRAFT,
                "Cancelamento tardio por motivo familiar comunicado no proprio dia.",
                "Adesao ao plano e barreiras logisticas",
                null,
                null,
                "Reagendar e confirmar plano de continuidade.",
                now.minus(2, ChronoUnit.DAYS),
                now
        ));
    }

    private static ClinicalCase clinicalCase(int suffix, UUID psychologistId, UUID patientId, String concern,
                                             ClinicalCaseStatus status, Instant openedAt, Instant closedAt,
                                             Instant now) {
        return ClinicalCase.rehydrate(
                new ClinicalCaseId(uuid("30000000-0000-4000-8000-00000000000" + suffix)),
                new PsychologistId(psychologistId),
                new PatientId(patientId),
                concern,
                status,
                openedAt,
                closedAt,
                openedAt,
                now
        );
    }

    private static List<CareGoal> goalsForCase(int suffix, LocalDate today) {
        return switch (suffix) {
            case 1 -> List.of(
                    goal(suffix, 1, "Reduzir frequencia de episodios de ansiedade intensa.", today.plusWeeks(6),
                            GoalStatus.IN_PROGRESS, 45),
                    goal(suffix, 2, "Melhorar rotina de sono com horario consistente.", today.plusWeeks(8),
                            GoalStatus.NOT_STARTED, 10)
            );
            case 2 -> List.of(
                    goal(suffix, 1, "Clarificar objetivos terapeuticos apos intake.", today.plusWeeks(3),
                            GoalStatus.NOT_STARTED, 0),
                    goal(suffix, 2, "Mapear rede de suporte familiar.", today.plusWeeks(5),
                            GoalStatus.IN_PROGRESS, 20)
            );
            case 3 -> List.of(
                    goal(suffix, 1, "Retomar atividades prazerosas semanais.", today.plusWeeks(6),
                            GoalStatus.PAUSED, 35)
            );
            case 4 -> List.of(
                    goal(suffix, 1, "Aplicar tecnicas de pausa em contexto laboral.", today.plusWeeks(4),
                            GoalStatus.IN_PROGRESS, 60),
                    goal(suffix, 2, "Definir limites de disponibilidade fora do horario.", today.plusWeeks(10),
                            GoalStatus.ACHIEVED, 100)
            );
            case 5 -> List.of(
                    goal(suffix, 1, "Consolidar plano de prevencao de recaida.", today.minusWeeks(1),
                            GoalStatus.ACHIEVED, 100),
                    goal(suffix, 2, "Encerrar acompanhamento regular.", today.minusDays(3),
                            GoalStatus.DISCONTINUED, 80)
            );
            default -> List.of(
                    goal(suffix, 1, "Identificar situacoes de evitamento social.", today.plusWeeks(4),
                            GoalStatus.NOT_STARTED, 0)
            );
        };
    }

    private static CareGoal goal(int caseSuffix, int goalSuffix, String description, LocalDate targetDate,
                                 GoalStatus status, int progress) {
        return new CareGoal(
                new CareGoalId(uuid("32000000-0000-4000-8000-0000000000" + caseSuffix + goalSuffix)),
                description,
                targetDate,
                status,
                progress
        );
    }

    private static String therapeuticFocus(int suffix) {
        return switch (suffix) {
            case 1 -> "Regulacao da ansiedade, higiene do sono e exposicao gradual.";
            case 2 -> "Avaliacao inicial, clarificacao de objetivos e suporte familiar.";
            case 3 -> "Monitorizacao de humor e manutencao de ganhos durante pausa.";
            case 4 -> "Gestao de stress laboral e limites pessoais.";
            case 5 -> "Consolidacao de estrategias e plano de alta.";
            default -> "Ansiedade social, exposicao progressiva e competencias de comunicacao.";
        };
    }

    private static String plannedFrequency(int suffix) {
        return suffix == 5 ? "Sessao de follow-up mensal" : "Sessao semanal de 50-60 minutos";
    }

    private static UUID patient(int suffix) {
        return uuid("10000000-0000-4000-8000-00000000000" + suffix);
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
