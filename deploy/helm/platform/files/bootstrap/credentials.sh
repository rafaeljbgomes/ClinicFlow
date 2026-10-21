# Shared helpers for the platform bootstrap scripts (ADR-026); sourced, not executed.

bootstrap_dir=${BOOTSTRAP_DIR:-$(dirname "$0")}
identities_file=${IDENTITIES_FILE:-$bootstrap_dir/service-identities.conf}
credentials_dir=${CREDENTIALS_DIR:-/credentials}

# Exports one service's generated credentials. Compose mounts the env files
# (<dir>/<service>.env); Kubernetes mounts each service Secret as <dir>/<service>/<KEY>.
load_credentials() {
    if [ -f "$credentials_dir/$1.env" ]; then
        set -a
        . "$credentials_dir/$1.env"
        set +a
    elif [ -d "$credentials_dir/$1" ]; then
        for credential_file in "$credentials_dir/$1"/*; do
            [ -f "$credential_file" ] || continue
            export "$(basename "$credential_file")=$(cat "$credential_file")"
        done
    else
        echo "No credentials for $1 in $credentials_dir; run scripts/generate-dev-credentials.ps1." >&2
        return 1
    fi
}

# Retries a command until it succeeds, then runs it once more with its output so the last error is visible.
retry() {
    attempt=1
    until "$@" >/dev/null 2>&1; do
        if [ "$attempt" -ge "${BOOTSTRAP_ATTEMPTS:-60}" ]; then
            "$@"
            return
        fi
        attempt=$((attempt + 1))
        sleep 2
    done
}
