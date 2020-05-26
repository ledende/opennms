#!/bin/sh

set -e

[ -n "$YUM_CONTAINER_NAME" ] || YUM_CONTAINER_NAME="yum-repo"

exec docker rm -f "${YUM_CONTAINER_NAME}" 2>/dev/null
