# ADR-032: A ClinicFlow login theme for Keycloak

Status: implemented. Date: 2026-10-31. Extends ADR-027.

## Context

ADR-027 moved sign-in and self-registration to Keycloak and kept Keycloak's
default `keycloak.v2` login theme, leaving a ClinicFlow theme for later. The
result was two visual languages in one sign-in. The app's `/login` and
`/register` pages use the frontend's tokens (deep green `#174b3a` on a warm
off-white, Instrument Sans and Instrument Serif, 20px cards, 12px controls) and
a brand panel beside the card. One click later the browser lands on Keycloak's
PatternFly page: a dark background image, an uppercase realm name, a blue card
edge and square controls. Error, expired-page and sign-out confirmation pages
looked the same way.

The pages Keycloak renders still have to be Keycloak's. Their forms, field ids,
scripts and messages are what the BFF flow, `scripts/check-keycloak-realm.py`,
the DAST login and the Playwright suites drive.

## Decision

### 1. A folder theme that extends keycloak.v2

`docker/keycloak/themes/clinicflow/login` is a login theme with
`parent=keycloak.v2`, copied into the image at `/opt/keycloak/themes`. The realm
file selects it with `"loginTheme": "clinicflow"`. Folder themes are read at
runtime, so the `kc.sh build` stage and `start --optimized` are unchanged, and
Compose and Kubernetes get the theme from the same image.

The theme owns four things:

- `template.ftl`, a copy of Keycloak 26.8.1's `keycloak.v2` layout changed in
  two marked places: the realm-name header becomes the app's brand panel, and
  the card gains the app's eyebrow and a per-page subtitle
  (`clinicflowSubtitle-<pageId>`). Every page template (`login.ftl`,
  `register.ftl`, errors, required actions) stays upstream's, so every Keycloak
  page gets the layout and no form changes.
- `css/clinicflow.css`, loaded after the parent stylesheet. It sets
  PatternFly 5's global variables to the frontend's tokens and restyles the
  login layout, fields, buttons, alerts and footer band to match the app's
  components.
- `messages/messages_en.properties`, the app's wording ("Sign in", "Create a
  patient account", "Need a patient account?").
- The fonts as WOFF2 files with their SIL Open Font License texts, and the
  app's favicon.

### 2. Self-hosted fonts, nothing from other origins

Instrument Sans (variable, 400 to 700) and Instrument Serif are bundled in the
theme, Latin subset only, about 50 KB together. The page loads every resource
from Keycloak's own origin, so it needs no Content-Security-Policy change and
works offline.

### 3. Light only

`darkMode=false`. The app opens in its light theme whatever the system setting
(`defaultTheme="light"` in `next-themes`), and the user's in-app choice lives in
the app's origin, which Keycloak cannot read. Following the operating system
would show a dark sign-in to users who see a light app.

### 4. The colours are tested against the frontend

`scripts/ci/tests/keycloak-theme.test.cjs` runs in the always-on repository
gate. It compares the theme's colour tokens with the light tokens in
`frontend/src/app/globals.css`, checks the realm, Dockerfile and theme wiring,
the template hooks upstream pages rely on, the message keys and the font
licences. `frontend/tests/e2e/keycloak-theme.spec.ts` checks the rendered pages
in the full-system Playwright run: layout, wording, primary and danger colours,
the font, no cross-origin requests and no sideways scroll on a phone.

## Alternatives considered

- **CSS only, on top of keycloak.v2.** It can recolour PatternFly but cannot add
  the brand panel or the card subtitle without generated content, which screen
  readers handle poorly and which cannot be translated through messages.
- **Keycloakify** (a React theme built into a JAR). It would let the frontend's
  components render Keycloak's pages, but it adds a Node build and a provider
  JAR to the Keycloak image, and every page becomes ClinicFlow's to maintain.
  The two marked template changes cost less for a prototype.
- **Overriding `login.ftl` and `register.ftl`.** Changing the pages themselves
  would risk the form ids and fields that the BFF flow and the tests drive. The
  layout template reaches every page without touching a form.
- **Loading fonts from Google Fonts.** It would make every sign-in call a third
  party and need a CSP change.
- **Theming the account console and email.** ClinicFlow does not link to the
  account console, and the realm sends no email (`verifyEmail` and password reset
  are off).

## Consequences

- Sign-in and registration read as one product across the app and Keycloak, at
  desktop and phone widths.
- `template.ftl` is a copy. When the Keycloak image is upgraded, compare it with
  the new `keycloak.v2/login/template.ftl` and carry upstream changes over; the
  repository test fails if a hook it depends on disappears, but cannot spot
  other upstream changes.
- A change to the frontend's light colour tokens fails the repository gate
  until the theme follows.
- Existing local realms keep Keycloak's default theme until
  `scripts/reset-keycloak-realm.ps1` re-imports the realm file, which removes
  self-registered users. CI always starts from the committed file.
- Theme files sit under `docker/keycloak`, so a theme change runs the full
  system and DAST jobs, like any other change to the identity provider.
