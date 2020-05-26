#!/usr/bin/env bash

set -e

# Exit script if a statement returns a non-true return value.
set -o errexit

# Use the error status of the first failure, rather than that of the last item in a pipeline.
set -o pipefail

RPMDIR="$1"; shift || :
PORT="$1"; shift || :
OCI="opennms/yum-repo:1.0.0-b4609"

[ -n "${CONTAINER_NAME}" ] || CONTAINER_NAME="yum-repo"
[ -n "${BUILD_NETWORK}"  ] || BUILD_NETWORK="opennms-build-network"

err_report() {
  echo "error on line $1" >&2
  echo "docker logs:" >&2
  echo "" >&2
  docker logs "${CONTAINER_NAME}" >&2
  exit 1
}

trap 'err_report $LINENO' ERR SIGHUP SIGINT SIGTERM

if [ -z "$RPMDIR" ]; then
  echo "usage: $0 <rpmdir> [port]"
  echo ""
  exit 1
fi
RPMDIR="$(cd "$RPMDIR"; pwd -P)"

if [ -z "$PORT" ]; then
  PORT=19990
fi

MYDIR="$(dirname "$0")"
MYDIR="$(cd "$MYDIR"; pwd -P)"

cd "$MYDIR"

echo "=== creating ${BUILD_NETWORK} network, if necessary ==="
./create_network.sh "${BUILD_NETWORK}"

echo "=== stopping old yum servers, if necessary ==="
./stop_yum_server.sh >/dev/null 2>&1 || :

echo "=== launching yum server ==="
docker run --rm --detach --name "${CONTAINER_NAME}" --volume "${RPMDIR}:/repo" --network "${BUILD_NETWORK}" --publish "${PORT}:${PORT}" "${OCI}"

echo "=== waiting for server to be available ==="
COUNT=0
while [ "$COUNT" -lt 30 ]; do
  COUNT="$((COUNT+1))"
  if [ "$( (docker logs "${CONTAINER_NAME}" 2>&1 || :) | grep -c 'server started' )" -gt 0 ]; then
    echo "READY"
    break
  fi
  sleep 1
done

if [ "$COUNT" -eq 30 ]; then
  echo "gave up waiting for server"
  echo "docker logs:"
  echo ""
  docker logs "${CONTAINER_NAME}"
  exit 1
fi
