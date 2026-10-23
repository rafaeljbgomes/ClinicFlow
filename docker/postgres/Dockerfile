# ClinicFlow's PostgreSQL image (ADR-028): the official image without gosu, always running as its
# postgres user (UID 70). Started as that user, the entrypoint never calls gosu, so the binary and the
# Go runtime it carries are removed instead of tracked.
FROM postgres:16.15-alpine@sha256:f4610795587492cd4fb887c2dc3de301b813278bb6f616992061141b5f15ac36 AS server

RUN rm /usr/local/bin/gosu

USER 70:70

# One-shot client that creates the service databases and identities (ADR-026); same base, same user.
FROM server AS bootstrap

COPY bootstrap/*.sql /bootstrap/
