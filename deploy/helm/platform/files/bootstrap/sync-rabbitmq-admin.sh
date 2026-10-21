#!/bin/sh
# Post-start hook of the RabbitMQ container (ADR-026). The image creates RABBITMQ_DEFAULT_USER only on
# an empty volume, so this re-applies its password on every start through rabbitmqctl, which the node
# trusts through its Erlang cookie. Rotating the administrator password is then: change the secret, restart.
set -eu

# The hook can start before the node process exists, which await_startup reports as an error at once.
attempt=1
until rabbitmqctl --quiet await_startup --timeout 10 >/dev/null 2>&1; do
    if [ "$attempt" -ge 60 ]; then
        rabbitmqctl await_startup --timeout 10
        exit 1
    fi
    attempt=$((attempt + 1))
    sleep 2
done
if rabbitmqctl --quiet list_users --silent | cut -f1 | grep -qxF "$RABBITMQ_DEFAULT_USER"; then
    rabbitmqctl --quiet change_password "$RABBITMQ_DEFAULT_USER" "$RABBITMQ_DEFAULT_PASS"
else
    rabbitmqctl --quiet add_user "$RABBITMQ_DEFAULT_USER" "$RABBITMQ_DEFAULT_PASS"
    rabbitmqctl --quiet set_user_tags "$RABBITMQ_DEFAULT_USER" administrator
    rabbitmqctl --quiet set_permissions -p / "$RABBITMQ_DEFAULT_USER" '.*' '.*' '.*'
fi
