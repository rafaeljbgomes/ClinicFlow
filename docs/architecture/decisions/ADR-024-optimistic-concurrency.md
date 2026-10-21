# ADR-024: Optimistic concurrency for mutable records

Status: implemented. Date: 2026-10-21. Issue: #49.

## Context

Every update in patient-service, appointment-service and clinical-service
loads a record, changes it in the domain, maps it to a new JPA entity and
saves it. The save writes every column, and nothing compared versions, so the
last writer won silently and both callers were told they had succeeded. Most
updates are full replacements built from a form, so the loss did not need
precise timing:

- a patient form opened before consent was revoked could grant it again and
  stamp a new `consentUpdatedAt`;
- a stale care-plan form deleted goals added since it was opened
  (`orphanRemoval`);
- a stale session-note edit overwrote newer clinical text;
- signing had no body, so a psychologist could sign text they never reviewed;
- case and patient status are set directly, so a stale screen could reopen a
  discharged case.

Overlapping transactions caused a worse failure. `complete` and `cancel` on
the same appointment both passed the state check and both events reached the
outbox (ADR-020). The stored status then contradicted the published events.

ADR-019's trigger already stopped any write to a signed note, and ADR-021's
ownership checks keep conflicts within one psychologist's own tabs and
devices. Neither addressed lost updates.

## Decision

Each aggregate root carries a version, and every write names the version it
was based on.

- **Persistence.** `patients`, `appointments`, `clinical_cases`, `care_plans`,
  `session_records` and `practice_profiles` gain `version BIGINT NOT NULL
  DEFAULT 0`, mapped as JPA `@Version Long`. Care goals have no version of
  their own: changing a plan always rewrites its root row, so the plan's
  version covers its goals. The wrapper type matters, because Spring Data
  persists an entity whose version is `null` and merges any other.
- **Domain and application.** Aggregates hold an opaque version that only
  persistence assigns: `null` from `create`, the stored value from
  `rehydrate`. Each update command carries the expected version. The
  application service compares it right after the ownership check and throws
  `StaleResourceVersionException` before any domain change.
- **Concurrent writes.** Repositories save with `saveAndFlush`, so the `@Version`
  predicate runs inside the service call. A lost race becomes the same
  exception, and the rolled-back transaction takes its outbox row with it.
  Repositories return the stored aggregate, so every response carries the new
  version.
- **HTTP contract.** Updates and actions require `If-Match: "<version>"`, a
  strong entity tag:

  | Request | Response |
  | --- | --- |
  | `If-Match` missing | `428 precondition_required` |
  | `If-Match` malformed, weak, `*` or a list | `400 invalid_precondition` |
  | Version is not the stored one | `412 stale_resource_version`, nothing changes |

  Single-record responses send `ETag`, and every record, in lists too, has a
  `version` field. Care plans and practice profiles are created by their first
  `PUT`, which sends `If-None-Match: *` instead. A concurrent first save then
  collides on the unique key and gets the same `412`, never a `500`. Creates,
  reads and the RabbitMQ consumer are unchanged.
- **Ordering with ADR-021.** The controller checks that the header is present
  and well formed before any lookup, so `428` and `400` are the same for every
  id. The version comparison runs only after the record is found and owned,
  so another psychologist's id still gets `404`, never `412`.
- **BFF and UI.** The BFF forwards only well-formed `If-Match` and
  `If-None-Match: *` on writes, and returns the `ETag`. Reads forward neither
  header, because browsers add `If-None-Match` to revalidations on their own.
  A form sends the version of the snapshot it opened with, not of data that
  SWR refreshed since. On `412` the form keeps what was typed and offers
  **Reload latest**, which replaces it with the stored version. Nothing is
  merged automatically. A stale signature is not retried: the psychologist
  has to review the latest note again.

Alternatives considered:

- **`@Version` without versions in the API** closes only overlapping
  transactions, not the more common stale forms.
- **Pessimistic locks** have the same gap and hold row locks.
- **`updatedAt` as the version** depends on clock precision; a counter is exact.
- **A `version` field in request bodies** is awkward for actions without a body
  (`complete`, `sign`) and is not the standard HTTP contract that the Bearer API
  offers to other clients.
- **Automatic field or three-way merges** are not safe for clinical text.

## Consequences

- Every update and action client must read before it writes and send the
  version it read. The BFF UI and the Playwright suite do; other Bearer API
  clients get `428` until they do.
- `412` means "your copy is stale"; the existing `409`s keep meaning a domain
  conflict, such as `session_record_signed`.
- Demo seeding resets fixed records on every start, so it now saves them with
  their stored version.
- The ADR-019 trigger stays as the backstop for writes that bypass versions,
  such as manual SQL. A stale write to a signed note is now rejected by the
  version check first.
- Event payloads do not carry the version yet. Issue #26 can reuse the column
  if consumers ever need to detect stale events.
