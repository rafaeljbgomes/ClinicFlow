#!/bin/sh
set -eu

if [ -n "${JENKINS_AGENT_SSH_PUBKEY_FILE:-}" ]; then
  if [ ! -r "$JENKINS_AGENT_SSH_PUBKEY_FILE" ]; then
    echo "Required agent public key file is missing: $JENKINS_AGENT_SSH_PUBKEY_FILE" >&2
    exit 1
  fi
  JENKINS_AGENT_SSH_PUBKEY="$(cat "$JENKINS_AGENT_SSH_PUBKEY_FILE")"
  export JENKINS_AGENT_SSH_PUBKEY
fi

if [ -S /var/run/docker.sock ]; then
  socket_gid="$(stat -c '%g' /var/run/docker.sock)"
  socket_group="$(getent group "$socket_gid" | cut -d: -f1 || true)"
  if [ -z "$socket_group" ]; then
    socket_group=docker-host
    groupadd --gid "$socket_gid" "$socket_group"
  fi
  usermod -aG "$socket_group" jenkins
fi

exec setup-sshd "$@"
