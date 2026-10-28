#!/bin/sh
# Local verification only: owns disposable Redis/Artemis; never deploys or pushes.
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

command -v docker >/dev/null 2>&1 || {
    echo "Docker is required for release verification." >&2
    exit 1
}
docker info >/dev/null

RUN_DIR=$(mktemp -d "${TMPDIR:-/tmp}/tracker-verify.XXXXXXXX")
RUN_TOKEN=${RUN_DIR##*/}
REDIS_NAME="$RUN_TOKEN-redis"
BROKER_NAME="$RUN_TOKEN-artemis"
remove_owned() {
    OWNER=$(docker inspect --format '{{index .Config.Labels "tracker.autonomous.run"}}' "$1" 2>/dev/null || true)
    if [ "$OWNER" = "$RUN_TOKEN" ]; then
        docker rm -f "$1" >/dev/null || echo "Could not clean owned container $1" >&2
    fi
}
cleanup() {
    remove_owned "$BROKER_NAME"
    remove_owned "$REDIS_NAME"
    rmdir "$RUN_DIR" || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

docker run -d --name "$REDIS_NAME" --label "tracker.autonomous.run=$RUN_TOKEN" \
    -p 127.0.0.1::6379 redis:7-alpine >/dev/null
REDIS_PORT=$(docker port "$REDIS_NAME" 6379/tcp | sed -n 's/^127\.0\.0\.1://p')
case "$REDIS_PORT" in
    ''|*[!0-9]*) echo "Could not discover disposable Redis port." >&2; exit 1 ;;
esac

READY=false
for attempt in 1 2 3 4 5 6 7 8 9 10; do
    if docker exec "$REDIS_NAME" redis-cli ping >/dev/null 2>&1; then
        READY=true
        break
    fi
    sleep 1
done
if [ "$READY" != true ]; then
    echo "Disposable Redis did not become ready." >&2
    exit 1
fi

docker run -d --name "$BROKER_NAME" --label "tracker.autonomous.run=$RUN_TOKEN" \
    -p 127.0.0.1::61616 -e ARTEMIS_USER=tracker_verify \
    -e ARTEMIS_PASSWORD=local-verification-only-password -e ANONYMOUS_LOGIN=false \
    -e 'EXTRA_ARGS=--http-host 0.0.0.0 --relax-jolokia --no-autotune --java-memory 512M' \
    apache/activemq-artemis:2.40.0-alpine >/dev/null
BROKER_PORT=$(docker port "$BROKER_NAME" 61616/tcp | sed -n 's/^127\.0\.0\.1://p')
case "$BROKER_PORT" in
    ''|*[!0-9]*) echo "Could not discover disposable Artemis port." >&2; exit 1 ;;
esac

# A mapped port is not a live broker: reject JVM startup failures before Maven.
READY=false
for attempt in $(seq 1 60); do
    if docker logs "$BROKER_NAME" 2>&1 | grep -q 'AMQ221007'; then
        READY=true
        break
    fi
    if [ "$(docker inspect --format '{{.State.Running}}' "$BROKER_NAME")" != true ]; then
        docker logs "$BROKER_NAME" >&2
        exit 1
    fi
    sleep 2
done
if [ "$READY" != true ]; then
    docker logs "$BROKER_NAME" >&2
    echo "Disposable Artemis did not become ready." >&2
    exit 1
fi

# Include dormant *IT suites explicitly; default Surefire does not select them.
# System properties isolate every application context, not just explicit Redis ITs.
# PostgresDeploymentIT supplies higher-priority dynamic properties for its own services.
./mvnw -B '-Dtest=*Test,*IntegrationTest,*IT' \
    "-Dtracker.test.redis.port=$REDIS_PORT" \
    -Dspring.data.redis.host=127.0.0.1 "-Dspring.data.redis.port=$REDIS_PORT" \
    -Dspring.data.redis.password= \
    "-Dspring.artemis.broker-url=tcp://127.0.0.1:$BROKER_PORT" \
    -Dspring.artemis.user=tracker_verify \
    -Dspring.artemis.password=local-verification-only-password clean verify
./mvnw -B -f device-comm/pom.xml \
    -Dspring.data.redis.host=127.0.0.1 "-Dspring.data.redis.port=$REDIS_PORT" \
    -Dspring.data.redis.password= \
    "-Dspring.artemis.broker-url=tcp://127.0.0.1:$BROKER_PORT" \
    -Dspring.artemis.user=tracker_verify \
    -Dspring.artemis.password=local-verification-only-password clean verify
