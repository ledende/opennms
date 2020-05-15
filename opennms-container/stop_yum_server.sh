#!/bin/sh

set -e

[ -n "$CONTAINER_NAME" ] || CONTAINER_NAME="yum-repo"

exec docker rm -f "${CONTAINER_NAME}" 2>/dev/null
