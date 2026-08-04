#!/bin/sh
set -eu

if [ -n "${JENKINS_AGENT_SSH_PUBKEY_FILE:-}" ]; then
  if [ ! -r "$JENKINS_AGENT_SSH_PUBKEY_FILE" ]; then
    echo "Required agent public key file is missing: $JENKINS_AGENT_SSH_PUBKEY_FILE" >&2
    exit 1
  fi
  JENKINS_AGENT_SSH_PUBKEY="$(tr -d '\r' < "$JENKINS_AGENT_SSH_PUBKEY_FILE")"
  export JENKINS_AGENT_SSH_PUBKEY
fi

if [ -n "${JENKINS_AGENT_SSH_HOST_KEY_FILE:-}" ]; then
  if [ ! -r "$JENKINS_AGENT_SSH_HOST_KEY_FILE" ] || [ ! -r "$JENKINS_AGENT_SSH_HOST_KEY_FILE.pub" ]; then
    echo "Required agent host key pair is missing: $JENKINS_AGENT_SSH_HOST_KEY_FILE" >&2
    exit 1
  fi
  tr -d '\r' < "$JENKINS_AGENT_SSH_HOST_KEY_FILE" > /etc/ssh/ssh_host_ed25519_key
  tr -d '\r' < "$JENKINS_AGENT_SSH_HOST_KEY_FILE.pub" > /etc/ssh/ssh_host_ed25519_key.pub
  chmod 600 /etc/ssh/ssh_host_ed25519_key
  chmod 644 /etc/ssh/ssh_host_ed25519_key.pub
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

# The SSH launcher owns this generated transport JAR. Starting with a clean
# target avoids an upstream SFTP overwrite failure after controller upgrades.
rm -f "${AGENT_WORKDIR:-/home/jenkins/agent}/remoting.jar"

exec setup-sshd "$@"
