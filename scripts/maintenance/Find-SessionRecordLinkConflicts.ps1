<#
.SYNOPSIS
Lists session records that hold another psychologist's or patient's appointment (ADR-025).

.DESCRIPTION
Before ADR-021 a manual session record could link any appointment id. Such a record blocks the real owner's
completion record because session_records.appointment_id is unique. clinical-service releases these links on its
own when the owner's appointment.completed event arrives or when the owner links the appointment, but it cannot
find them ahead of time: appointment ownership lives in appointment-service's database.

This script reads both databases and joins them locally. It reports:
  Unresolved          a record holds an appointment of another psychologist or patient
  MissingAppointment  a record links an appointment id that appointment-service does not know
  Released            a conflict clinical-service has already resolved (session_record_link_conflicts)

It changes nothing unless -Replay is given. -Replay republishes appointment.completed, built from
appointment-service's own row, for each unresolved conflict whose appointment is already COMPLETED. These are the
completions the consumer skipped before ADR-025. The message goes straight to the clinical queue, so notification-service
does not see it again.

.EXAMPLE
./scripts/maintenance/Find-SessionRecordLinkConflicts.ps1 | Format-Table

.EXAMPLE
./scripts/maintenance/Find-SessionRecordLinkConflicts.ps1 -Platform Kubernetes -Namespace clinicflow

.EXAMPLE
./scripts/maintenance/Find-SessionRecordLinkConflicts.ps1 -Replay -RabbitMqCredential (Get-Credential)
#>
[CmdletBinding(SupportsShouldProcess, ConfirmImpact = 'High')]
param(
    [ValidateSet('Compose', 'Kubernetes')]
    [string]$Platform = 'Compose',
    [string]$ComposeProjectName,
    [string]$Namespace = 'clinicflow',
    [string]$PostgresPod = 'postgres-0',
    [string]$PostgresUser = 'clinicflow',
    [string]$AppointmentDatabase = 'clinicflow_appointment',
    [string]$ClinicalDatabase = 'clinicflow_clinical',
    [switch]$Replay,
    [uri]$RabbitMqManagementUrl = 'http://localhost:15672',
    [pscredential]$RabbitMqCredential,
    [string]$RabbitMqVirtualHost = '/',
    [string]$ClinicalQueue = 'clinical.appointment-events.queue'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Replay -and -not $RabbitMqCredential) {
    throw '-Replay needs -RabbitMqCredential for the RabbitMQ management API.'
}

$RepositoryRoot = Resolve-Path (Join-Path $PSScriptRoot '../..')

function Invoke-Psql {
    param(
        [Parameter(Mandatory)][string]$Database,
        [Parameter(Mandatory)][string]$Query
    )

    $PsqlArguments = @('psql', '-U', $PostgresUser, '-d', $Database, '-v', 'ON_ERROR_STOP=1', '--csv', '-c', $Query)
    if ($Platform -eq 'Compose') {
        $ComposeArguments = @('compose', '-f', (Join-Path $RepositoryRoot 'docker-compose.yml'))
        if ($ComposeProjectName) {
            $ComposeArguments += @('-p', $ComposeProjectName)
        }
        $Output = & docker @ComposeArguments exec -T postgres @PsqlArguments
    } else {
        $Output = & kubectl exec -n $Namespace $PostgresPod -- @PsqlArguments
    }
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed against $Database (exit $LASTEXITCODE)."
    }
    return @($Output | ConvertFrom-Csv)
}

$Appointments = @{}
foreach ($Appointment in Invoke-Psql -Database $AppointmentDatabase -Query @'
SELECT id, psychologist_id, patient_id, status, type,
       to_char(scheduled_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS scheduled_at
FROM appointments
'@) {
    $Appointments[$Appointment.id] = $Appointment
}

$LinkedRecords = Invoke-Psql -Database $ClinicalDatabase -Query @'
SELECT id, psychologist_id, patient_id, appointment_id, note_status
FROM session_records
WHERE appointment_id IS NOT NULL
ORDER BY created_at
'@

$Unresolved = [System.Collections.Generic.List[object]]::new()
foreach ($Record in $LinkedRecords) {
    $Appointment = $Appointments[$Record.appointment_id]
    if (-not $Appointment) {
        [pscustomobject]@{
            Category = 'MissingAppointment'
            AppointmentId = $Record.appointment_id
            AppointmentStatus = $null
            SessionRecordId = $Record.id
            NoteStatus = $Record.note_status
            NextStep = 'Review manually: no appointment has this id.'
        }
        continue
    }
    if ($Record.psychologist_id -eq $Appointment.psychologist_id -and $Record.patient_id -eq $Appointment.patient_id) {
        continue
    }
    $NextStep = switch ($Appointment.status) {
        'COMPLETED' { 'Completion was skipped: replay it with -Replay.' }
        'CANCELLED' { 'Never completes: released if the owner links it; otherwise leave as is.' }
        default { 'Released automatically when the appointment completes.' }
    }
    $Conflict = [pscustomobject]@{
        Category = 'Unresolved'
        AppointmentId = $Record.appointment_id
        AppointmentStatus = $Appointment.status
        SessionRecordId = $Record.id
        NoteStatus = $Record.note_status
        NextStep = $NextStep
    }
    $Unresolved.Add($Conflict)
    $Conflict
}

Invoke-Psql -Database $ClinicalDatabase -Query @'
SELECT conflict.appointment_id, conflict.session_record_id, record.note_status, conflict.source, conflict.detected_at
FROM session_record_link_conflicts conflict
JOIN session_records record ON record.id = conflict.session_record_id
ORDER BY conflict.detected_at
'@ | ForEach-Object {
    [pscustomobject]@{
        Category = 'Released'
        AppointmentId = $_.appointment_id
        AppointmentStatus = $null
        SessionRecordId = $_.session_record_id
        NoteStatus = $_.note_status
        NextStep = "Released by $($_.source) at $($_.detected_at)."
    }
}

if (-not $Replay) {
    return
}

$PublishUri = [uri]::new($RabbitMqManagementUrl,
    "api/exchanges/$([uri]::EscapeDataString($RabbitMqVirtualHost))/amq.default/publish")
foreach ($Conflict in $Unresolved | Where-Object AppointmentStatus -eq 'COMPLETED') {
    $Appointment = $Appointments[$Conflict.AppointmentId]
    if (-not $PSCmdlet.ShouldProcess("appointment $($Appointment.id)", 'Replay appointment.completed to the clinical queue')) {
        continue
    }
    # Same shape as event-contracts/.../appointment-completed.json; the owner comes from appointment-service's row.
    $Event = [ordered]@{
        eventId = [guid]::NewGuid().ToString()
        eventType = 'AppointmentCompleted'
        occurredAt = [DateTimeOffset]::UtcNow.ToString("yyyy-MM-dd'T'HH:mm:ss.fff'Z'")
        appointmentId = $Appointment.id
        patientId = $Appointment.patient_id
        psychologistId = $Appointment.psychologist_id
        appointmentDate = $Appointment.scheduled_at
        appointmentType = $Appointment.type
    }
    $Body = @{
        properties = @{
            content_type = 'application/json'
            delivery_mode = 2
            headers = @{ 'X-Correlation-Id' = [guid]::NewGuid().ToString() }
        }
        routing_key = $ClinicalQueue
        payload = ($Event | ConvertTo-Json -Compress)
        payload_encoding = 'string'
    } | ConvertTo-Json -Depth 4 -Compress
    $Response = Invoke-RestMethod -Method Post -Uri $PublishUri -Credential $RabbitMqCredential -Authentication Basic `
        -AllowUnencryptedAuthentication -ContentType 'application/json' -Body $Body
    if (-not $Response.routed) {
        throw "RabbitMQ did not route the replay for appointment $($Appointment.id) to $ClinicalQueue."
    }
    Write-Information "Replayed appointment.completed for $($Appointment.id) (event $($Event.eventId))." `
        -InformationAction Continue
}
