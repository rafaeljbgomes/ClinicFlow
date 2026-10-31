# ClinicFlow frontend

Next.js 16 and React 19 frontend for ClinicFlow. It exposes a browser-facing BFF, so service URLs and bearer tokens never need to be handled by client components.

## Local development

```bash
npm run dev
```

Open `http://localhost:3000`. Configure the four services with `PATIENT_SERVICE_URL`, `APPOINTMENT_SERVICE_URL`, `CLINICAL_SERVICE_URL`, and `NOTIFICATION_SERVICE_URL`. The BFF signs users in through Keycloak with the authorization-code flow and PKCE, and needs `CLINICFLOW_OIDC_ISSUER`, `CLINICFLOW_PUBLIC_URL` and `CLINICFLOW_BFF_CLIENT_SECRET` (see [BFF sign-in](../docs/development/local-development.md#bff-sign-in)). The administrator's account lookup also needs `CLINICFLOW_USER_LOOKUP_CLIENT_SECRET`; without it the lookup answers `503`.

## Access model

- Psychologists: today, patients, calendar, clinical records, scoped practice messages, and practice settings.
- Administrators: service health, delivery-event history, account lookup, and security settings.
- Patients: a protected account notice until a dedicated patient portal is implemented.

`proxy.ts` only performs optimistic cookie checks. The dashboard layout and role-protected BFF routes verify the Keycloak access token locally and read the profile from its claims. The account lookup reads Keycloak's Admin API with a read-only service account (ADR-031). Spring Boot services remain the authoritative authorization boundary. Mutations require the double-submit CSRF token, including logout.

## Quality checks

```bash
npm run typecheck
npm run lint
npm run build
npm run test:e2e
```
