#!/bin/sh
# Creates or updates every messaging service's RabbitMQ user and its permissions on vhost "/" (ADR-026).
# The caller sets the management API target (RABBITMQADMIN_TARGET_HOST/_PORT) and the platform
# administrator login (RABBITMQADMIN_USERNAME/_PASSWORD); rabbitmqadmin reads them from the
# environment, which keeps the login out of the process list. Each run re-applies passwords and
# permissions, so it also rotates credentials.
set -eu
. "$(dirname "$0")/credentials.sh"
export RABBITMQADMIN_NON_INTERACTIVE_MODE=true RABBITMQADMIN_QUIET_MODE=true

# Topic exchanges whose routing keys are limited per service. The exchange-level permissions alone
# would let any publisher send any event and any consumer bind its queue with "#".
topic_exchanges="therapy.events.exchange therapy.events.dlx"

json_string() {
    printf '"%s"' "$(printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g')"
}

retry rabbitmqadmin show overview

# Topic permissions can only name an existing exchange. Declare them exactly as the services do
# (durable, not auto-deleted), so the services' own declarations stay idempotent.
for exchange in $topic_exchanges; do
    rabbitmqadmin declare exchange --name "$exchange" --type topic --durable true --auto-delete false
done

while read -r service _database configure write read topic_write topic_read; do
    case "$service" in ''|'#'*) continue ;; esac
    [ "$configure" = "-" ] && continue
    (
        load_credentials "$service"
        # Service users get no tags, so they cannot use the management API.
        rabbitmqadmin users declare --name "$SPRING_RABBITMQ_USERNAME" --password "$SPRING_RABBITMQ_PASSWORD" --tags ""
        rabbitmqadmin permissions declare --vhost / --username "$SPRING_RABBITMQ_USERNAME" \
            --configure "$configure" --write "$write" --read "$read"
        # rabbitmqadmin has no topic-permission command; a definitions import merges them instead.
        separator=""
        {
            printf '{"topic_permissions":['
            for exchange in $topic_exchanges; do
                printf '%s{"user":%s,"vhost":"/","exchange":%s,"write":%s,"read":%s}' "$separator" \
                    "$(json_string "$SPRING_RABBITMQ_USERNAME")" "$(json_string "$exchange")" \
                    "$(json_string "$topic_write")" "$(json_string "$topic_read")"
                separator=","
            done
            printf ']}'
        } | rabbitmqadmin definitions import --stdin
    )
    echo "RabbitMQ user ready for $service."
done < "$identities_file"
