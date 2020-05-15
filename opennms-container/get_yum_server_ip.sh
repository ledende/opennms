#!/bin/sh

[ -n "$CONTAINER_NAME" ] || CONTAINER_NAME="yum-repo"
[ -n "${BUILD_NETWORK}"  ] || BUILD_NETWORK="opennms-build-network"

exec docker network inspect "${BUILD_NETWORK}" 2>/dev/null | jq --raw-output  ".[].Containers | if .[].Name == \"${CONTAINER_NAME}\" then (.[].IPv4Address) else empty end" | sed -e 's,/[^/]*$,,'
