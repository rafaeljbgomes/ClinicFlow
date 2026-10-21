#!/bin/sh
# Creates or updates every messaging service's RabbitMQ user and its permissions on vhost "/" (ADR-026).
# The caller sets the management API target (RABBITMQADMIN_TARGET_HOST/_PORT) and the platform
# administrator login (RABBITMQADMIN_USERNAME/_PASSWORD); rabbitmqadmin reads them from the
# environment, which keeps the login out of the process list. Each run re-applies passwords and
# permissions, so it also rotates credentials.
set -eu
. "$(dirname "$0")/credentials.sh"
export RABBITMQADMIN_NON_INTERACTIVE_MODE=true RABBITMQADMIN_QUIET_MODE=true

retry rabbitmqadmin show overview

while read -r service _database configure write read; do
    case "$service" in ''|'#'*) continue ;; esac
    [ "$configure" = "-" ] && continue
    (
        load_credentials "$service"
        # Service users get no tags, so they cannot use the management API.
        rabbitmqadmin users declare --name "$SPRING_RABBITMQ_USERNAME" --password "$SPRING_RABBITMQ_PASSWORD" --tags ""
        rabbitmqadmin permissions declare --vhost / --username "$SPRING_RABBITMQ_USERNAME" \
            --configure "$configure" --write "$write" --read "$read"
    )
    echo "RabbitMQ user ready for $service."
done < "$identities_file"
