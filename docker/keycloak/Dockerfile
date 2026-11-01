# ClinicFlow's Keycloak image (ADR-027). Build-time options are fixed here, so `start --optimized`
# skips the Quarkus re-augmentation that plain `start` runs on every boot. The relative paths are
# Keycloak's defaults, persisted explicitly: the Kubernetes chart sets them at runtime, and
# `--optimized` refuses to start when a runtime value differs from the persisted one.
FROM quay.io/keycloak/keycloak:26.8.1@sha256:a78d6b79572168cb25886b80a8b5b8d4ed4390743e3eecb810538683e209dd6b AS builder

ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true \
    KC_HTTP_RELATIVE_PATH=/ \
    KC_HTTP_MANAGEMENT_RELATIVE_PATH=/

RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.8.1@sha256:a78d6b79572168cb25886b80a8b5b8d4ed4390743e3eecb810538683e209dd6b

# A runtime option since Keycloak 25, so it belongs on this stage: one replica needs no cluster,
# while the default ispn cache would start JGroups with JDBC_PING discovery on every boot.
ENV KC_CACHE=local

COPY --from=builder /opt/keycloak/ /opt/keycloak/
COPY --chmod=0555 healthcheck.sh /opt/clinicflow/healthcheck.sh
# The realm is imported only when it does not exist yet; see docs/development/local-development.md.
COPY realm/clinicflow-realm.json /opt/keycloak/data/import/clinicflow-realm.json
# The realm's login theme. Folder themes are read at runtime, so they need no `kc.sh build`; owned by
# root, they stay read-only to the Keycloak user.
COPY themes/ /opt/keycloak/themes/

USER 1000
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm"]
