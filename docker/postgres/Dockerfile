# ClinicFlow's PostgreSQL image (ADR-028): the official image without gosu, always running as its
# postgres user (UID 70). Started as that user, the entrypoint never calls gosu, so the binary and the
# Go runtime it carries are removed instead of tracked.
FROM postgres:18.4-alpine@sha256:9a8afca54e7861fd90fab5fdf4c42477a6b1cb7d293595148e674e0a3181de15 AS server

RUN rm /usr/local/bin/gosu

USER 70:70

# One-shot client that creates the service databases and identities (ADR-026); same base, same user.
FROM server AS bootstrap

COPY bootstrap/*.sql /bootstrap/
