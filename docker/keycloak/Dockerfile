# ClinicFlow's Keycloak image (ADR-027). Build-time options are fixed here, so `start --optimized`
# skips the Quarkus re-augmentation that plain `start` runs on every boot.
FROM quay.io/keycloak/keycloak:26.8.1@sha256:79c623a22abacf3fe6c00a5fb30f5b7843d717b234033b80995891c06116d244 AS builder

ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true \
    KC_CACHE=local

RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.8.1@sha256:79c623a22abacf3fe6c00a5fb30f5b7843d717b234033b80995891c06116d244

COPY --from=builder /opt/keycloak/ /opt/keycloak/
COPY --chmod=0555 healthcheck.sh /opt/clinicflow/healthcheck.sh
# The realm is imported only when it does not exist yet; see docs/development/local-development.md.
COPY realm/clinicflow-realm.json /opt/keycloak/data/import/clinicflow-realm.json

USER 1000
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm"]
