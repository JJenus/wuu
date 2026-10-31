#!/bin/sh
# Start the tracking-engine backend with disposable loopback dependencies.
#
# Local development only: containers are ephemeral and must never be treated as
# a production deployment. See README.md and SECURITY.md for deployment rules.
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
RESOURCE_DIR="$ROOT/src/main/resources"
LABEL_KEY="tracker.start.run"
MVN="$ROOT/mvnw"

PROFILE=""
MODE="docker"
KEEP_DEPS=0
TIMEOUT=120
SKIP_TESTS=0

REDIS_NAME=""
ARTEMIS_NAME=""
POSTGRES_NAME=""
APP_PID=""
STARTED_DEPS=0
DEPS_REPORTED=0
CLEANED=0

fail() {
    printf '[tracker] error: %s\n' "$*" >&2
    exit 1
}

available_profiles() {
    {
        printf '%s\n' default
        for file in "$RESOURCE_DIR"/application-*.yaml "$RESOURCE_DIR"/application-*.yml; do
            [ -f "$file" ] || continue
            name=${file##*/application-}
            name=${name%.yaml}
            name=${name%.yml}
            case "$name" in
                ''|*[!a-zA-Z0-9_.-]*) continue ;;
            esac
            printf '%s\n' "$name"
        done
    } | sort -u
}

profile_csv() {
    available_profiles | paste -sd ',' - | sed 's/,/, /g'
}

usage() {
    cat <<EOF
Usage: $0 [PROFILE] [options]

Start the backend locally with disposable Redis, Artemis and (for the postgres
profile) PostgreSQL containers. Containers listen on random loopback ports,
use generated credentials, and are removed when the backend exits.

Profiles: $(profile_csv)

Options:
  -p, --profile NAME   Spring profile to activate (default: default)
      --external-deps  Use already-running dependencies instead of Docker
      --no-docker      Alias for --external-deps
      --keep-deps      Leave started containers running after the backend exits
      --timeout SECS   Dependency readiness timeout in seconds (default: 120)
      --skip-tests     Skip Maven test compilation and execution during startup
      --list-profiles  Print available Spring profiles and exit
  -h, --help           Show this help

Examples:
  $0                          # H2 + disposable Redis/Artemis
  $0 --profile postgres       # PostgreSQL + disposable Redis/Artemis
  $0 postgres                 # Same, using the positional profile form
  ARTEMIS_USER=... ARTEMIS_PASSWORD=... $0 --external-deps

Environment:
  JWT_SECRET         Reused when set; otherwise a random secret is generated.
  SERVER_PORT        Backend HTTP port (Spring default: 8080).
EOF
}

random_hex() {
    random_bytes=$1
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -hex "$random_bytes"
    elif command -v od >/dev/null 2>&1; then
        od -An -N "$random_bytes" -tx1 /dev/urandom | tr -d ' \n'
    else
        fail "Cannot generate a secret: install openssl or od."
    fi
}

ensure_docker() {
    docker info >/dev/null 2>&1 && return 0
    # Never start a local daemon for a remote Docker context.
    endpoint=${DOCKER_HOST:-$(docker context inspect --format '{{.Endpoints.docker.Host}}' 2>/dev/null || true)}
    case "$endpoint" in
        unix://*) ;;
        *) fail "Docker is unreachable. Start the configured daemon or use --external-deps." ;;
    esac
    command -v systemctl >/dev/null 2>&1 \
        || fail "Start Docker manually on this platform, then retry."
    printf '[tracker] attempting to start the local Docker service...\n'
    if [ "$endpoint" = "unix:///run/user/$(id -u)/docker.sock" ]; then
        systemctl --user start docker \
            || fail "Could not start user Docker; start it manually, then retry."
    elif [ "$(id -u)" -eq 0 ]; then
        systemctl start docker || fail "Could not start Docker; start it manually, then retry."
    elif command -v sudo >/dev/null 2>&1; then
        sudo -n systemctl start docker \
            || fail "Cannot start Docker without interactive authorization. Run sudo systemctl start docker, then retry."
    else
        fail "Start Docker manually, then retry (sudo is unavailable)."
    fi
    docker_deadline=$(( $(date +%s) + TIMEOUT ))
    while [ "$(date +%s)" -lt "$docker_deadline" ]; do
        docker info >/dev/null 2>&1 && return 0
        sleep 1
    done
    fail "Docker is still unreachable after ${TIMEOUT}s. Check daemon status and socket permissions."
}

discover_port() {
    container_name=$1
    container_port=$2
    host_port=$(docker port "$container_name" "$container_port/tcp" 2>/dev/null \
        | sed -n 's/.*://p' | head -n 1)
    case "$host_port" in
        ''|*[!0-9]*)
            fail "Could not discover the loopback port for $container_name ($container_port/tcp)."
            ;;
    esac
    printf '%s\n' "$host_port"
}

container_running() {
    [ "$(docker inspect --format '{{.State.Running}}' "$1" 2>/dev/null || true)" = "true" ]
}

show_logs() {
    printf '[tracker] last log lines from %s:\n' "$1" >&2
    docker logs --tail 40 "$1" >&2 || true
}

wait_for_redis() {
    deadline=$(( $(date +%s) + TIMEOUT ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        if docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" "$REDIS_NAME" \
            redis-cli ping 2>/dev/null | grep -q '^PONG$'; then
            return 0
        fi
        if ! container_running "$REDIS_NAME"; then
            show_logs "$REDIS_NAME"
            return 1
        fi
        sleep 1
    done
    printf '[tracker] Redis did not become ready within %ss.\n' "$TIMEOUT" >&2
    show_logs "$REDIS_NAME"
    return 1
}

wait_for_artemis() {
    deadline=$(( $(date +%s) + TIMEOUT ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        if docker logs "$ARTEMIS_NAME" 2>&1 | grep -q 'AMQ221007'; then
            return 0
        fi
        if ! container_running "$ARTEMIS_NAME"; then
            show_logs "$ARTEMIS_NAME"
            return 1
        fi
        sleep 1
    done
    printf '[tracker] Artemis did not become ready within %ss.\n' "$TIMEOUT" >&2
    show_logs "$ARTEMIS_NAME"
    return 1
}

wait_for_postgres() {
    deadline=$(( $(date +%s) + TIMEOUT ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        if docker exec "$POSTGRES_NAME" pg_isready -U "$DB_USER" -d "$DB_NAME" \
            >/dev/null 2>&1; then
            return 0
        fi
        if ! container_running "$POSTGRES_NAME"; then
            show_logs "$POSTGRES_NAME"
            return 1
        fi
        sleep 1
    done
    printf '[tracker] PostgreSQL did not become ready within %ss.\n' "$TIMEOUT" >&2
    show_logs "$POSTGRES_NAME"
    return 1
}

start_redis() {
    printf '[tracker] starting disposable Redis...\n'
    docker run -d --name "$REDIS_NAME" --label "$LABEL_KEY=$RUN_TOKEN" \
        -p 127.0.0.1::6379 redis:7-alpine \
        redis-server --requirepass "$REDIS_PASSWORD" --save "" --appendonly no \
        >/dev/null
    REDIS_HOST_PORT=$(discover_port "$REDIS_NAME" 6379)
}

start_artemis() {
    printf '[tracker] starting disposable Artemis...\n'
    docker run -d --name "$ARTEMIS_NAME" --label "$LABEL_KEY=$RUN_TOKEN" \
        -p 127.0.0.1::61616 \
        -e ARTEMIS_USER="$ARTEMIS_USER" \
        -e ARTEMIS_PASSWORD="$ARTEMIS_PASSWORD" \
        -e ANONYMOUS_LOGIN=false \
        -e 'EXTRA_ARGS=--http-host 0.0.0.0 --relax-jolokia --no-autotune --java-memory 512M' \
        apache/activemq-artemis:2.40.0-alpine >/dev/null
    ARTEMIS_HOST_PORT=$(discover_port "$ARTEMIS_NAME" 61616)
}

start_postgres() {
    printf '[tracker] starting disposable PostgreSQL...\n'
    docker run -d --name "$POSTGRES_NAME" --label "$LABEL_KEY=$RUN_TOKEN" \
        -p 127.0.0.1::5432 \
        -e POSTGRES_DB="$DB_NAME" \
        -e POSTGRES_USER="$DB_USER" \
        -e POSTGRES_PASSWORD="$DB_PASSWORD" \
        postgres:16-alpine >/dev/null
    DB_HOST_PORT=$(discover_port "$POSTGRES_NAME" 5432)
}

remove_owned() {
    name=$1
    [ -n "$name" ] || return 0
    owner=$(docker inspect \
        --format "{{ index .Config.Labels \"$LABEL_KEY\" }}" \
        "$name" 2>/dev/null || true)
    if [ "$owner" = "$RUN_TOKEN" ]; then
        docker rm -f "$name" >/dev/null 2>&1 \
            || printf '[tracker] warning: could not remove container %s\n' "$name" >&2
    fi
}

cleanup() {
    [ "$CLEANED" -eq 1 ] && return 0
    CLEANED=1

    if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
        kill -TERM "$APP_PID" 2>/dev/null || true
        waited=0
        while kill -0 "$APP_PID" 2>/dev/null && [ "$waited" -lt 10 ]; do
            sleep 1
            waited=$((waited + 1))
        done
        kill -KILL "$APP_PID" 2>/dev/null || true
    fi

    if [ "$MODE" != "docker" ] || [ "$STARTED_DEPS" -eq 0 ]; then
        return 0
    fi

    if [ "$KEEP_DEPS" -eq 1 ]; then
        if [ "$DEPS_REPORTED" -eq 0 ]; then
            DEPS_REPORTED=1
            printf '\n[tracker] dependencies left running:\n'
            printf '  redis:    %s\n' "$REDIS_NAME"
            printf '  artemis:  %s\n' "$ARTEMIS_NAME"
            if [ -n "$POSTGRES_NAME" ]; then
                printf '  postgres: %s\n' "$POSTGRES_NAME"
            fi
            printf '[tracker] remove them with: docker rm -f'
            printf ' %s' "$REDIS_NAME" "$ARTEMIS_NAME"
            if [ -n "$POSTGRES_NAME" ]; then
                printf ' %s' "$POSTGRES_NAME"
            fi
            printf '\n'
        fi
        return 0
    fi

    printf '[tracker] removing disposable dependencies...\n'
    remove_owned "$ARTEMIS_NAME"
    remove_owned "$POSTGRES_NAME"
    remove_owned "$REDIS_NAME"
}

on_interrupt() {
    cleanup
    exit 130
}

on_terminate() {
    cleanup
    exit 143
}

on_hangup() {
    cleanup
    exit 129
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        -h|--help)
            usage
            exit 0
            ;;
        --list-profiles)
            available_profiles
            exit 0
            ;;
        -p|--profile)
            [ "$#" -ge 2 ] || fail "Option $1 requires a profile name."
            [ -z "$PROFILE" ] || fail "Profile was specified more than once."
            PROFILE=$2
            shift 2
            ;;
        --profile=*)
            [ -z "$PROFILE" ] || fail "Profile was specified more than once."
            PROFILE=${1#*=}
            [ -n "$PROFILE" ] || fail "--profile requires a profile name."
            shift
            ;;
        --external-deps|--no-docker)
            MODE="external"
            shift
            ;;
        --keep-deps)
            KEEP_DEPS=1
            shift
            ;;
        --skip-tests)
            SKIP_TESTS=1
            shift
            ;;
        --timeout)
            [ "$#" -ge 2 ] || fail "Option --timeout requires a number of seconds."
            TIMEOUT=$2
            shift 2
            ;;
        --timeout=*)
            TIMEOUT=${1#*=}
            shift
            ;;
        --)
            shift
            if [ "$#" -gt 0 ]; then
                [ -z "$PROFILE" ] || fail "Profile was specified more than once."
                PROFILE=$1
                shift
            fi
            [ "$#" -eq 0 ] || fail "Unexpected arguments after --."
            ;;
        -*)
            fail "Unknown option: $1"
            ;;
        *)
            [ -z "$PROFILE" ] || fail "Profile was specified more than once."
            PROFILE=$1
            shift
            ;;
    esac
done

case "$TIMEOUT" in
    ''|*[!0-9]*) fail "--timeout must be a positive integer." ;;
esac
[ "$TIMEOUT" -gt 0 ] || fail "--timeout must be a positive integer."

[ -n "$PROFILE" ] || PROFILE="default"
case "$PROFILE" in
    *[!a-zA-Z0-9_.-]*) fail "Invalid profile name: $PROFILE" ;;
esac
if ! available_profiles | grep -Fqx "$PROFILE"; then
    fail "Unknown profile '$PROFILE'. Available profiles: $(profile_csv)"
fi

if [ "$MODE" = "external" ] && [ "$KEEP_DEPS" -eq 1 ]; then
    fail "--keep-deps only applies when this script starts Docker dependencies."
fi

cd "$ROOT"
[ -x "$MVN" ] || fail "Maven wrapper is not executable: $MVN"

if [ "$PROFILE" = "default" ]; then
    SPRING_PROFILES_ACTIVE=""
else
    SPRING_PROFILES_ACTIVE="$PROFILE"
fi
export SPRING_PROFILES_ACTIVE

if [ -n "${JWT_SECRET:-}" ]; then
    JWT_GENERATED=0
else
    JWT_SECRET=$(random_hex 48)
    JWT_GENERATED=1
fi
[ "${#JWT_SECRET}" -ge 32 ] \
    || fail "JWT_SECRET must be at least 32 bytes."
export JWT_SECRET

trap cleanup EXIT
trap on_interrupt INT
trap on_terminate TERM
trap on_hangup HUP

if [ "$MODE" = "docker" ]; then
    command -v docker >/dev/null 2>&1 \
        || fail "Docker is required to start disposable dependencies."
    ensure_docker

    RUN_TOKEN="tracker-start-$(random_hex 6)-$$"
    REDIS_NAME="$RUN_TOKEN-redis"
    ARTEMIS_NAME="$RUN_TOKEN-artemis"
    [ "$PROFILE" = "postgres" ] && POSTGRES_NAME="$RUN_TOKEN-postgres"

    # Do not let inherited Spring-native settings redirect the backend away
    # from the disposable containers this script owns.
    unset SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD
    unset SPRING_DATA_REDIS_HOST SPRING_DATA_REDIS_PORT SPRING_DATA_REDIS_PASSWORD
    unset SPRING_ARTEMIS_BROKER_URL SPRING_ARTEMIS_HOST SPRING_ARTEMIS_PORT
    unset SPRING_ARTEMIS_USER SPRING_ARTEMIS_PASSWORD
    unset DB_URL DB_USERNAME DB_PASSWORD
    unset REDIS_HOST REDIS_PORT REDIS_PASSWORD
    unset ARTEMIS_HOST ARTEMIS_PORT ARTEMIS_BROKER_URL ARTEMIS_USER ARTEMIS_PASSWORD

    REDIS_PASSWORD=$(random_hex 24)
    ARTEMIS_USER="tracker"
    ARTEMIS_PASSWORD=$(random_hex 24)
    DB_NAME="tracking"
    DB_USER="tracker"
    DB_PASSWORD=$(random_hex 24)

    STARTED_DEPS=1
    start_redis
    start_artemis
    if [ -n "$POSTGRES_NAME" ]; then
        start_postgres
    fi

    wait_for_redis
    wait_for_artemis
    if [ -n "$POSTGRES_NAME" ]; then
        wait_for_postgres
    fi

    export REDIS_HOST="127.0.0.1"
    export REDIS_PORT="$REDIS_HOST_PORT"
    export REDIS_PASSWORD
    export ARTEMIS_HOST="127.0.0.1"
    export ARTEMIS_PORT="$ARTEMIS_HOST_PORT"
    export ARTEMIS_USER ARTEMIS_PASSWORD
    if [ -n "$POSTGRES_NAME" ]; then
        export DB_URL="jdbc:postgresql://127.0.0.1:$DB_HOST_PORT/$DB_NAME"
        export DB_USERNAME="$DB_USER"
        export DB_PASSWORD
        export ARTEMIS_BROKER_URL="tcp://127.0.0.1:$ARTEMIS_HOST_PORT"
        DEPS="Redis 127.0.0.1:$REDIS_HOST_PORT, Artemis tcp://127.0.0.1:$ARTEMIS_HOST_PORT, PostgreSQL 127.0.0.1:$DB_HOST_PORT/$DB_NAME"
    else
        DEPS="Redis 127.0.0.1:$REDIS_HOST_PORT, Artemis tcp://127.0.0.1:$ARTEMIS_HOST_PORT"
    fi
else
    # External dependencies: require explicit broker credentials instead of
    # silently accepting the default admin/admin pair.
    [ -n "${ARTEMIS_USER:-}" ] \
        || fail "Set ARTEMIS_USER when using --external-deps."
    [ -n "${ARTEMIS_PASSWORD:-}" ] \
        || fail "Set ARTEMIS_PASSWORD when using --external-deps."

    : "${ARTEMIS_HOST:=127.0.0.1}"
    : "${ARTEMIS_PORT:=61616}"
    : "${REDIS_HOST:=127.0.0.1}"
    : "${REDIS_PORT:=6379}"
    export ARTEMIS_HOST ARTEMIS_PORT ARTEMIS_USER ARTEMIS_PASSWORD
    export REDIS_HOST REDIS_PORT
    [ -z "${REDIS_PASSWORD:-}" ] || export REDIS_PASSWORD

    if [ "$PROFILE" = "postgres" ]; then
        [ -n "${DB_URL:-}" ] || fail "Set DB_URL when using --external-deps with postgres."
        [ -n "${DB_USERNAME:-}" ] \
            || fail "Set DB_USERNAME when using --external-deps with postgres."
        [ -n "${DB_PASSWORD:-}" ] \
            || fail "Set DB_PASSWORD when using --external-deps with postgres."
        export DB_URL DB_USERNAME DB_PASSWORD
        [ -n "${ARTEMIS_BROKER_URL:-}" ] \
            || ARTEMIS_BROKER_URL="tcp://$ARTEMIS_HOST:$ARTEMIS_PORT"
        export ARTEMIS_BROKER_URL
        DEPS="external Redis $REDIS_HOST:$REDIS_PORT, Artemis $ARTEMIS_BROKER_URL, PostgreSQL configured by DB_URL"
    else
        DEPS="external Redis $REDIS_HOST:$REDIS_PORT, Artemis tcp://$ARTEMIS_HOST:$ARTEMIS_PORT"
    fi
fi

if [ "$JWT_GENERATED" -eq 1 ]; then
    printf '[tracker] generated an ephemeral JWT_SECRET for this run\n'
fi
printf '[tracker] profile: %s\n' "$PROFILE"
printf '[tracker] dependencies: %s\n' "$DEPS"
printf '[tracker] backend health: http://127.0.0.1:%s/actuator/health\n' "${SERVER_PORT:-8080}"
printf '[tracker] starting backend; press Ctrl-C to stop\n\n'

# fork=false keeps the application in Maven's JVM, so the trap above owns the
# complete process and can shut it down cleanly.
set -- -B -Dspring-boot.run.fork=false
if [ "$SKIP_TESTS" -eq 1 ]; then
    # Boot run invokes test-compile; skipTests alone only skips execution.
    set -- "$@" -Dmaven.test.skip=true
fi
"$MVN" "$@" spring-boot:run &
APP_PID=$!

set +e
wait "$APP_PID"
APP_STATUS=$?
set -e
APP_PID=""

exit "$APP_STATUS"
