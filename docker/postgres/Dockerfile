# ClinicFlow's PostgreSQL image (ADR-028): the official image without gosu, always running as its
# postgres user (UID 70). Started as that user, the entrypoint never calls gosu, so the binary and the
# Go runtime it carries are removed instead of tracked.
FROM postgres:16.16-alpine@sha256:1d5b02d3a9941fbd52437db4107f8c52d6297dac553ca1e5c299ebb3756b4857 AS server

RUN rm /usr/local/bin/gosu

USER 70:70

# One-shot client that creates the service databases and identities (ADR-026); same base, same user.
FROM server AS bootstrap

COPY bootstrap/*.sql /bootstrap/
