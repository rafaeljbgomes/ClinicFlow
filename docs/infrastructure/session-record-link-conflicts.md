# Session-record link conflicts

Runbook for [ADR-025](../architecture/architectural-decisions.md#adr-025-appointment-owners-reclaim-conflicting-session-record-links).
Before ADR-021, a manual session record could link another psychologist's
appointment id. Because `session_records.appointment_id` is unique, that record
blocks the owner's completion record. ClinicFlow uses synthetic data only, so
this affects local and demo databases written before ADR-021.

## What resolves on its own

clinical-service releases the conflicting link and records it in
`session_record_link_conflicts` when one of these happens:

- the owner's `appointment.completed` event arrives (source `APPOINTMENT_COMPLETED`);
- the owner creates a session record linked to the appointment through
  `POST /clinical-cases/{id}/session-records`, after appointment-service has
  verified the owner (source `MANUAL_LINK`).

The released record keeps its note, status and signature. Only its
`appointment_id` becomes null. Each release logs:

```text
session_record_appointment_conflict appointmentId=<id> sessionRecordId=<id> source=<source> resolution=link_released
```

## What needs an operator

Before ADR-025, the consumer skipped completions that hit a conflict. Those events
are gone: the outbox keeps published rows for seven days (ADR-020), and the clinical
consumer acknowledged them. The frontend does not let a psychologist link an
appointment to a manual record, so these appointments need a replay.

1. List the conflicts. The script is read-only unless you pass `-Replay`.

   ```powershell
   ./scripts/maintenance/Find-SessionRecordLinkConflicts.ps1 | Format-Table
   ```

   For Kubernetes add `-Platform Kubernetes -Namespace clinicflow`. For an isolated
   Compose project add `-ComposeProjectName <name>`.

2. Read the `Category` and `NextStep` columns:

   | Category | Meaning | Action |
   | --- | --- | --- |
   | `Unresolved`, appointment `SCHEDULED` or `RESCHEDULED` | Released when the appointment completes. | None. |
   | `Unresolved`, appointment `COMPLETED` | The completion was skipped before ADR-025. | Replay (step 3). |
   | `Unresolved`, appointment `CANCELLED` | It never completes, so no owner record is due. | Leave it, or release it when the owner links it. |
   | `MissingAppointment` | No appointment has this id. | Review manually; clinical-service does not change it. |
   | `Released` | Already resolved. | None. |

3. Replay the skipped completions. The script builds `appointment.completed` from
   appointment-service's row and publishes it to `clinical.appointment-events.queue`
   through the default exchange, so notification-service does not receive it
   again. It asks for confirmation for each appointment.

   ```powershell
   ./scripts/maintenance/Find-SessionRecordLinkConflicts.ps1 -Replay -RabbitMqCredential (Get-Credential)
   ```

   Use the RabbitMQ administrator login from `secrets/platform/rabbitmq.env`
   (ADR-026); service users have no management API access. The script's
   `-PostgresUser` defaults to `clinicflow`, the administrator name that
   `scripts/generate-dev-credentials.ps1` writes to `secrets/platform/postgres.env`.

   For Kubernetes, first forward the management port
   (`kubectl port-forward -n clinicflow svc/rabbitmq 15672`) and pass the
   Kubernetes options from step 1.

4. Run step 1 again. Each replayed appointment should now be `Released` with
   source `APPOINTMENT_COMPLETED`.

A replay is safe to repeat. Once the owner's record holds the appointment, the
consumer ignores further completions for it.
