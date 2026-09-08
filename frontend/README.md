# ClinicFlow frontend

Next.js 16 and React 19 frontend for ClinicFlow. It exposes a browser-facing BFF, so service URLs and bearer tokens never need to be handled by client components.

## Local development

```bash
npm run dev
```

Open `http://localhost:3000`. Configure the five services with `AUTH_SERVICE_URL`, `PATIENT_SERVICE_URL`, `APPOINTMENT_SERVICE_URL`, `CLINICAL_SERVICE_URL`, and `NOTIFICATION_SERVICE_URL`. The BFF also requires `CLINICFLOW_SECURITY_JWT_ISSUER` and either `CLINICFLOW_SECURITY_JWT_PUBLIC_KEY` or `CLINICFLOW_SECURITY_JWT_PUBLIC_KEY_PATH`.

## Access model

- Psychologists: today, patients, calendar, clinical records, scoped practice messages, and practice settings.
- Administrators: service health, delivery-event history, account lookup, and security settings.
- Patients: a protected account notice until a dedicated patient portal is implemented.

`proxy.ts` only performs optimistic cookie checks. The dashboard layout calls auth-service for the full user profile; role-protected BFF routes verify signed JWT claims locally before forwarding requests. Spring Boot services remain the authoritative authorization boundary. Mutations require the double-submit CSRF token, including logout.

## Quality checks

```bash
npm run typecheck
npm run lint
npm run build
npm run test:e2e
```
