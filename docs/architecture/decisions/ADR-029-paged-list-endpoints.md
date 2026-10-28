# ADR-029: Paged list endpoints

Status: implemented for appointments, notifications, clinical cases and session
records; patients to follow. Date: 2026-10-23, extended to clinical-service on
2026-10-28.

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

  `from` must be before `to`. `order=DESC` serves views of past sessions,
  which read latest first. Clinical cases and session records read latest
  first, which is how the clinical workspace already sorted them in the
  browser. The session-record list still checks that the caller owns the case
  before it reads a page, so a foreign case id stays a `404`.
- **Layers.** Each service has its own `PageQuery`, `PageResult` and
  `PageResponse`, in keeping with ADR-001's no-shared-library rule. Spring
  Data's `Pageable`, `Page` and `Specification` stay inside the persistence
  adapters, so the application ports keep their own types.
- **BFF.** List routes forward only an allowlist of query parameters
  (`forwardedQuery`), never the raw query string. The psychologist projection
  of notifications now maps `content`, and anything that is not a page is
  withheld instead of passed through unprojected.
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
  records page with Previous/Next.

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
  `GET /clinical-cases` and `GET /clinical-cases/{id}/session-records` for
  Bearer API clients. The BFF, the UI, the Playwright suite and the gateway
  acceptance test are updated in the same change as each endpoint.
  `GET /clinical-cases` also accepts `status` more than once now, where it took
  a single value before.
- `totalElements` costs a `count(*)` per request. That is cheap on the
  indexes above, but deep pages still scan the offset, so keyset paging is the
  next step if it ever matters.
- Rows inserted between two page requests can shift later pages by one. The
  calendar and the message screens refresh on their own, which is acceptable
  for them.
- The calendar's patient picker and name lookups still read the full patient
  list, so the pages remain usable until patients are paged.
- Patient names live in patient-service, so the case browser's text search
  narrows only the page on screen; the status filter and paging are
  server-side. A server-side search by patient waits for patient search.
- A case the psychologist picks can leave both loaded pages, for example when
  its status changes away from the browser's filter. The workspace then reads
  that case on its own with `GET /clinical-cases/{id}` rather than losing the
  selection.
- Not done yet:
  - `GET /patients`, which the appointment and clinical screens use as a
    lookup table and picker. It needs search and lookup-by-ids first.
  - `GET /patients/{id}/clinical-history` stays unpaged on purpose: it is one
    aggregate document rather than a list.
