#!/usr/bin/env python3
"""Owned loopback TCP/Artemis/PostgreSQL/Redis/browser acceptance. Never deploys."""
import argparse
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import signal
import socket
import subprocess
import tempfile
import threading
import time
from types import FrameType
import urllib.request


@dataclass(frozen=True)
class Registration:
    email: str
    password: str
    first_name: str
    last_name: str
    organization_name: str


@dataclass(frozen=True)
class VehicleInput:
    vehicle_id: str
    license_plate: str


@dataclass(frozen=True)
class TrackerInput:
    tracker_id: str
    device_id: str
    vehicle_id: str


@dataclass(frozen=True)
class TokenResponse:
    access_token: str


def free_port() -> int:
    with socket.socket() as server:
        server.bind(("127.0.0.1", 0))
        return server.getsockname()[1]


def stop(process: subprocess.Popen[str]) -> None:
    if process.poll() is not None:
        return
    process.terminate()
    try:
        process.wait(timeout=20)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artemis-home", type=Path, required=True)
    parser.add_argument("--frontend", type=Path, default=Path(__file__).resolve().parents[2] / "app")
    parser.add_argument("--pg-bin", type=Path, default=Path("/usr/lib/postgresql/18/bin"))
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    artemis = args.artemis_home.resolve() / "bin/artemis"
    jars = [root / "target/tracking-engine-1.0.0-SNAPSHOT.jar",
            root / "device-comm/target/device-comm-1.0.0-SNAPSHOT.jar"]
    for required in [artemis, args.pg_bin / "initdb", *jars,
                     args.frontend / ".output/server/index.mjs"]:
        if not required.is_file():
            parser.error(f"Build/install prerequisite first: {required}")
    base = Path(tempfile.mkdtemp(prefix="telemetry-native-", dir="/tmp/opencode"))
    print(f"Owned telemetry evidence: {base}", flush=True)
    pg_port, redis_port, broker_port, java_port, tcp_port = [free_port() for _ in range(5)]
    processes: list[subprocess.Popen[str]] = []
    pg_started = False

    def interrupted(signum: int, _frame: FrameType | None) -> None:
        raise SystemExit(128 + signum)

    signal.signal(signal.SIGTERM, interrupted)

    def start(label: str, command: list[str], marker: str, env: dict[str, str]) -> subprocess.Popen[str]:
        ready = threading.Event()
        process = subprocess.Popen(command, env=env, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, text=True)
        processes.append(process)

        def record() -> None:
            with (base / f"{label}.log").open("w") as output:
                assert process.stdout is not None
                for line in process.stdout:
                    output.write(line)
                    output.flush()
                    if marker in line:
                        ready.set()
            ready.set()  # Fail immediately on early process exit, not after the timeout.

        threading.Thread(target=record, daemon=True).start()
        if not ready.wait(100) or process.poll() is not None:
            raise RuntimeError(f"{label} did not start; see {base}")
        return process

    def sql(query: str) -> str:
        return subprocess.check_output([str(args.pg_bin / "psql"), "-h", "127.0.0.1", "-p", str(pg_port),
                                        "-U", "tracking_map_test", "-d", "tracking_map_test", "-At",
                                        "-v", "ON_ERROR_STOP=1", "-c", query], text=True).strip()

    broker_url = f"tcp://127.0.0.1:{broker_port}"
    env = {**os.environ, "JWT_SECRET": "test-only-telemetry-jwt-secret-at-least-32-characters",
           "REGISTRATION_ENABLED": "true", "DB_URL": f"jdbc:postgresql://127.0.0.1:{pg_port}/tracking_map_test",
           "DB_USERNAME": "tracking_map_test", "DB_PASSWORD": "", "ARTEMIS_BROKER_URL": broker_url,
           "ARTEMIS_URL": broker_url + "?initialConnectAttempts=0&reconnectAttempts=0&callTimeout=2000",
           "ARTEMIS_USER": "telemetry_test", "ARTEMIS_PASSWORD": "test-only-broker-password",
           "REDIS_HOST": "127.0.0.1", "REDIS_PORT": str(redis_port)}

    def wait_for_intake_subscription(label: str) -> None:
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            result = subprocess.run([str(artemis), "queue", "stat", "--json", "--silent", "--url", broker_url,
                                     "--user", env["ARTEMIS_USER"], "--password", env["ARTEMIS_PASSWORD"],
                                     "--field", "ADDRESS", "--operation", "EQUALS", "--value",
                                     "tracking.events.locationdataevent"], capture_output=True, text=True, timeout=15)
            (base / f"{label}-subscription.log").write_text(result.stdout + result.stderr)
            # CLI JSON is an external reporting boundary, not an application DTO.
            if result.returncode == 0 and re.search(r'"consumerCount"\s*:\s*"?[1-9][0-9]*"?', result.stdout):
                return
            threading.Event().wait(0.5)
        raise RuntimeError(f"No live location-event subscription; see {base}")

    try:
        with (base / "init.log").open("w") as output:
            subprocess.run([str(args.pg_bin / "initdb"), "-D", str(base / "pgdata"), "-U",
                            "tracking_map_test", "--auth=trust", "--no-locale"], check=True,
                           stdout=output, stderr=subprocess.STDOUT)
        subprocess.run([str(args.pg_bin / "pg_ctl"), "-D", str(base / "pgdata"), "-l", str(base / "postgres.log"),
                        "-o", f"-h 127.0.0.1 -p {pg_port} -k {base}", "-w", "start"], check=True)
        pg_started = True
        subprocess.run([str(args.pg_bin / "createdb"), "-h", "127.0.0.1", "-p", str(pg_port),
                        "-U", "tracking_map_test", "tracking_map_test"], check=True)
        start("redis", ["redis-server", "--bind", "127.0.0.1", "--port", str(redis_port), "--save", "",
                        "--appendonly", "no"], "Ready to accept connections", env)
        with (base / "broker-create.log").open("w") as output:
            subprocess.run([str(artemis), "create", "--silent", "--require-login", "--user", env["ARTEMIS_USER"],
                            "--password", env["ARTEMIS_PASSWORD"], "--host", "127.0.0.1", "--default-port",
                            str(broker_port), "--no-web", "--no-autotune", "--nio", "--no-amqp-acceptor",
                            "--no-mqtt-acceptor", "--no-stomp-acceptor", "--no-hornetq-acceptor",
                            "--java-memory", "512M", str(base / "broker")], check=True,
                           stdout=output, stderr=subprocess.STDOUT)
        broker_command = [str(base / "broker/bin/artemis"), "run"]
        broker = start("broker", broker_command, "AMQ221007", env)
        start("backend", ["java", "-Xmx384m", "-jar", str(jars[0]), "--spring.profiles.active=postgres",
                          "--server.address=127.0.0.1", f"--server.port={java_port}",
                          f"--spring.data.redis.port={redis_port}", "--userauth.password.bcrypt-strength=4",
                          "--tracking.alert-outbox.poll-delay-ms=3600000"], "Started TrackingEngineApplication", env)
        origin = f"http://127.0.0.1:{java_port}"

        def post(path: str, body: Registration | VehicleInput | TrackerInput, token: str | None = None) -> bytes:
            headers = {"content-type": "application/json"}
            if token:
                headers["authorization"] = "Bearer " + token
            request = urllib.request.Request(origin + path, data=json.dumps(asdict(body)).encode(), headers=headers)
            with urllib.request.urlopen(request, timeout=20) as response:
                return response.read()

        for label, device in [("owner", "8168000008"), ("foreign", "8168000009")]:
            # Decode unknown HTTP JSON only here and retain a concrete token response.
            registration = json.loads(post("/api/v1/auth/register", Registration(
                label + "@telemetry-test.example", "eight123", "Telemetry", label, "Disposable telemetry " + label)))
            if not isinstance(registration, dict) or not isinstance(registration.get("access_token"), str):
                raise ValueError("Registration returned no access token")
            account = TokenResponse(registration["access_token"])
            vehicle = "tracking-map-vehicle" if label == "owner" else "telemetry-foreign"
            post("/api/v1/vehicles", VehicleInput(vehicle, "LIVE MAP 01" if label == "owner" else "FOREIGN MAP"), account.access_token)
            post("/api/v1/trackers", TrackerInput("tracker-" + label, device, vehicle), account.access_token)
        wait_for_intake_subscription("initial")
        start("device", ["java", "-Xmx256m", "-jar", str(jars[1]), "--tracking.tcp.server.host=127.0.0.1",
                         f"--tracking.tcp.server.port={tcp_port}"], "TCP Server started successfully", env)
        # Stop only our broker. A failed publish must close TCP, save no location,
        # and never be silently replayed under a newly generated event identity.
        stop(broker)
        now = datetime.now(timezone.utc)
        frame = f"*HQ,8168000008,V1,{now:%H%M%S},A,5130.0000,N,00006.0000,W,012.96,000,{now:%d%m%y},FBFBBFF,460,00,10342,4283,10,25,128#"
        with socket.create_connection(("127.0.0.1", tcp_port), timeout=20) as peer:
            peer.sendall(frame.encode("ascii"))
            if peer.recv(1) != b"":
                raise AssertionError("Failed publication did not close device connection")
        if sql("SELECT count(*) FROM tracker_location_data") != "0":
            raise AssertionError("Failed publication unexpectedly saved a location")
        start("broker-recovered", broker_command, "AMQ221007", env)
        wait_for_intake_subscription("recovered")
        if sql("SELECT count(*) FROM tracker_location_data") != "0":
            raise AssertionError("An unacknowledged failed packet was silently replayed")
        print("PASS: broker outage closes TCP; recovered intake subscriber is active", flush=True)
        test_env = {**env, "TRACKING_TEST_JAVA_ORIGIN": origin, "TRACKING_TEST_EMAIL": "owner@telemetry-test.example",
                    "TRACKING_TEST_PASSWORD": "eight123", "TRACKING_TEST_POSTGRES_PORT": str(pg_port),
                    "TRACKING_TEST_TCP_PORT": str(tcp_port)}
        with (base / "browser.log").open("w") as output:
            subprocess.run(["pnpm", "exec", "playwright", "test", "--config", "playwright.tracking.config.ts"],
                           cwd=args.frontend, env=test_env, stdout=output, stderr=subprocess.STDOUT, check=True, timeout=180)
        print("PASS: fresh TCP telemetry crosses recovered Artemis into persistence and browser refresh", flush=True)
    finally:
        for process in reversed(processes):
            stop(process)
        if pg_started:
            subprocess.run([str(args.pg_bin / "pg_ctl"), "-D", str(base / "pgdata"), "-m", "fast", "-w", "stop"], check=True)


if __name__ == "__main__":
    main()
