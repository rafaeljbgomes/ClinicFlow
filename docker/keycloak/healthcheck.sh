#!/bin/bash
# Compose healthcheck: healthy once the server is ready and the clinicflow realm exists.
# The image has no curl, so it speaks HTTP over bash's /dev/tcp. Only the status line counts: the
# /health/ready body lists passing sub-checks as "UP" while the overall status is still DOWN.
set -euo pipefail

http_status() {
    exec 3<>"/dev/tcp/127.0.0.1/$1"
    printf 'GET %s HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' "$2" >&3
    read -r _ status _ <&3
    exec 3<&-
    [ "$status" = 200 ]
}

http_status 9000 /health/ready
http_status 8080 /realms/clinicflow/.well-known/openid-configuration
