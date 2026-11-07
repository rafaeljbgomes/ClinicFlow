# ADR-029: Paged list endpoints

Status: implemented for appointments, notifications, clinical cases, session
records and patients. Date: 2026-10-23, extended to clinical-service on
2026-10-28 and to patient-service on 2026-11-07.

## Context

Every list endpoint returned the caller's whole history as a JSON array:
`GET /appointments`, `GET /notifications`, `GET /patients`,
`GET /clinical-cases` and `GET /clinical-cases/{id}/session-records`. Each
read loaded every row into the JVM, mapped it twice and serialised it, and the
BFF and the browser then held all of it. The dashboard fetched four full lists
just to count a few things and show eight upcoming sessions and three
messages.

These lists grow at different rates. A practice adds a few patients a month,
but every session adds an appointment, several notifications and a session
record. The administrator's event history reads every psychologist's
notifications with no `ORDER BY`. So response size, query time and memory grew
with the history, and a long-running practice would eventually hit the BFF's
10 s timeout or Traefik's 30 s one.

Three defects came with this:

- notifications were ordered newest-first for psychologists but unordered for
  administrators;
- the dashboard's "Recent messages" assumed oldest-first and showed the
  three oldest messages;
- the calendar had nothing to sort or filter by except what was already in
  the browser.

## Decision

List endpoints return one bounded page in a fixed order, and they filter on
the server.

- **Request.** `page` counts from 0 (default 0). `size` defaults to 20 and must
  be 1-100. Anything outside that, or a filter the service cannot parse, is a
  `400` problem before any query runs: `invalid_request` for out-of-range
  values, `invalid_parameter` for malformed ones. There is no unpaged mode.
- **Response.** The same shape as Spring Data's `PagedModel`, so clients
  already know it:

  ```json
  { "content": [ ... ],
    "page": { "size": 20, "number": 0, "totalElements": 41, "totalPages": 3 } }
  ```

  A page past the end is empty but keeps the totals.
- **Fixed, total order.** Clients cannot sort by arbitrary fields. Each
  resource has one documented order with the id as tie-breaker, so rows never
  repeat or disappear between pages, and every order is backed by an index:

  | Endpoint | Filters | Order | Index |
  | --- | --- | --- | --- |
  | `GET /notifications` | `status` (repeatable) | `createdAt` desc, `id` desc | `(psychologist_id, created_at DESC)`; new `(created_at DESC, id DESC)` for the administrator view |
  | `GET /appointments` | `from`, `to` (ISO-8601 instants, half-open `[from, to)`), `status` (repeatable) | `scheduledAt` then `id`, `order=ASC` (default) or `DESC` | new `(psychologist_id, scheduled_at, id)`, which replaces `(psychologist_id)` |
  | `GET /clinical-cases` | `patientId`, `status` (repeatable) | `openedAt` desc, `id` desc | new `(psychologist_id, opened_at DESC, id DESC)`, which replaces `(psychologist_id)` |
  | `GET /clinical-cases/{id}/session-records` | none | `sessionDate` desc, `id` desc | new `(clinical_case_id, session_date DESC, id DESC)`, which replaces `(clinical_case_id)` |
  | `GET /patients` | `q`, `status` (repeatable), `consent` (repeatable), `createdFrom` (ISO-8601 instant, inclusive), `id` (repeatable, at most 100) | `fullName` ignoring case, then `id` | new `(psychologist_id, lower(full_name), id)`, which replaces `(psychologist_id)` |

  `from` must be before `to`. `order=DESC` serves views of past sessions,
  which read latest first. Clinical cases and session records read latest
  first, which is how the clinical workspace already sorted them in the
  browser. The session-record list still checks that the caller owns the case
  before it reads a page, so a foreign case id stays a `404`.
- **Patient search and lookup.** `q` matches the full name, the preferred name
  or the email, case-insensitively and anywhere in the value. LIKE wildcards
  in it are matched literally, and it is at most 100 characters. It is not
  index-backed; a practice has hundreds of patients, so a scan of one
  psychologist's rows is fine, and a trigram index can follow if it ever is
  not. `id` looks up specific patients, so the screens that show patient names
  next to appointments or cases ask only for the patients on screen. Ids that
  are not the caller's patients are simply absent, as with every other filter.
- **Layers.** Each service has its own `PageQuery`, `PageResult` and
  `PageResponse`, in keeping with ADR-001's no-shared-library rule. Spring
  Data's `Pageable`, `Page` and `Specification` stay inside the persistence
  adapters, so the application ports keep their own types.
- **BFF.** List routes forward only an allowlist of query parameters
  (`forwardedQuery`), never the raw query string. A parameter outside the
  allowlist is a `400` problem with code `unsupported_parameter`, answered
  before the service is called and without echoing the name. It used to be
  dropped silently, so a mistyped filter returned an unfiltered list, and two
  requests that differed only in an unknown parameter could get different
  pages because the list changed in between; the authenticated DAST scan read
  that difference as a boolean SQL injection on `/api/notifications`. The
  psychologist projection of notifications now maps `content`, and anything
  that is not a page is withheld instead of passed through unprojected.
- **UI.** The dashboard asks the server for what each card shows: the next
  eight open sessions, `totalElements` of open sessions in the next seven days,
  the three newest messages, and `totalElements` of `PENDING`/`FAILED`
  messages. The calendar has Upcoming (from today, earliest first) and Past
  (before today, latest first) views and a server-side status filter. The
  calendar and the message screens page with Previous/Next. The dashboard's
  case metric and intake follow-ups come from `status=ACTIVE` and
  `status=INTAKE` pages. In the clinical workspace, the case switcher offers
  the 20 most recently opened cases and the total, the case browser filters
  by status on the server and pages through every case, and a case's session
  records page with Previous/Next. The patient registry searches, filters by
  status and pages on the server. The appointment and case forms pick a
  patient from a server-side search that offers 20 matches at a time. The
  calendar, the clinical workspace and the dashboard look up names with `id`
  for the rows on screen. The dashboard's patient metric counts with
  `totalElements` (all patients, and `createdFrom` the start of the month),
  and its consent follow-ups come from a `consent=PENDING&consent=REVOKED`
  page.

Alternatives considered:

- **Keyset (cursor) paging.** It is cheaper for deep pages and stable under
  concurrent inserts. A practice's history is thousands of rows, not
  millions, so offset paging with an index is fast enough. It also gives page
  numbers and totals, which the dashboard uses as counts. Keyset paging can
  be added per endpoint later behind the same response shape.
- **A JSON array with `Link`/`X-Total-Count` headers.** This keeps the body
  backward compatible, but the BFF projection and SWR would have to carry
  headers, and the gateway and CORS would have to expose them.
- **An optional `page` parameter that falls back to the full list.** This
  leaves the unbounded read reachable, which is the problem being removed.
- **Separate summary endpoints for the dashboard.** These would mean more API
  surface. Filters plus `totalElements` give the same counts with one
  contract.

## Consequences

- This is a breaking change to `GET /appointments`, `GET /notifications`,
  `GET /clinical-cases`, `GET /clinical-cases/{id}/session-records` and
  `GET /patients` for Bearer API clients. The BFF, the UI, the Playwright
  suite and the gateway acceptance test are updated in the same change as
  each endpoint.
  `GET /clinical-cases` also accepts `status` more than once now, where it took
  a single value before.
- `totalElements` costs a `count(*)` per request. That is cheap on the
  indexes above, but deep pages still scan the offset, so keyset paging is the
  next step if it ever matters.
- Rows inserted between two page requests can shift later pages by one. The
  calendar and the message screens refresh on their own, which is acceptable
  for them.
- The patient forms no longer preselect the first patient: the picker offers
  a page of matches and the psychologist chooses one. A newly created patient
  can sort onto a later registry page, so the registry searches for it after
  creating it.
- The unpaged patient list was also the largest response the authenticated
  DAST scan compared: hundreds of scanner-created rows in one JSON line. Its
  template-injection rule accepts an expected number anywhere between two of
  its markers, so a digit run in an unrelated row could raise a false High
  alert. One bounded page per response makes such a match far less likely.
- Patient names live in patient-service, so the case browser's text search
  narrows only the page on screen; the status filter and paging are
  server-side. The patient search could now find the matching patients
  first, but the case browser does not use it yet.
- A case the psychologist picks can leave both loaded pages, for example when
  its status changes away from the browser's filter. The workspace then reads
  that case on its own with `GET /clinical-cases/{id}` rather than losing the
  selection.
- `GET /patients/{id}/clinical-history` stays unpaged on purpose: it is one
  aggregate document rather than a list.
