#!/usr/bin/env bash
set -Eeuo pipefail

readonly REPOSITORY_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly INSTALL_DIR="/opt/docker-watchdog"
readonly ENV_FILE="$INSTALL_DIR/.env"
readonly UNIT_FILE="docker-watchdog.service"

fail() {
    local exit_code=$?
    printf 'Deployment failed (exit code %d). The watchdog service was not restarted.\n' "$exit_code" >&2
    exit "$exit_code"
}

trap fail ERR

printf 'Pulling latest changes...\n'
git -C "$REPOSITORY_DIR" pull --ff-only

printf 'Building watchdog...\n'
mvn -f "$REPOSITORY_DIR/pom.xml" clean package

if [[ ! -f "$ENV_FILE" ]]; then
    printf 'Deployment failed: missing %s. Create it with the SMTP and watchdog settings before redeploying.\n' "$ENV_FILE" >&2
    exit 1
fi

shopt -s nullglob
jars=("$REPOSITORY_DIR"/target/docker-watchdog-*.jar)
if (( ${#jars[@]} != 1 )); then
    printf 'Deployment failed: expected one packaged watchdog JAR, found %d.\n' "${#jars[@]}" >&2
    exit 1
fi

printf 'Installing %s...\n' "${jars[0]##*/}"
sudo install -d -o root -g root -m 755 "$INSTALL_DIR"
sudo install -o root -g root -m 644 "${jars[0]}" "$INSTALL_DIR/${jars[0]##*/}"
sudo install -o root -g root -m 644 "$REPOSITORY_DIR/$UNIT_FILE" "/etc/systemd/system/$UNIT_FILE"

printf 'Reloading and starting the watchdog service...\n'
sudo systemctl daemon-reload
sudo systemctl enable --now docker-watchdog

if sudo systemctl is-active --quiet docker-watchdog; then
    printf 'Deployment succeeded: docker-watchdog is active.\n'
else
    printf 'Deployment failed: docker-watchdog is not active.\n' >&2
    sudo systemctl status docker-watchdog --no-pager || true
    exit 1
fi
