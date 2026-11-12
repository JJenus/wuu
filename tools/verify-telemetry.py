#!/usr/bin/env python3
"""Owned loopback TCP/Artemis/PostgreSQL/Redis/browser acceptance. Never deploys."""
import argparse
from dataclasses import asdict, dataclass
from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import tempfile
import threading
import time
from types import FrameType
from typing import Callable
import urllib.request
import xml.etree.ElementTree as XML


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


@dataclass(frozen=True)
class OverspeedRule:
    rule_key: str
    rule_name: str
    speed_limit: int
    vehicle_ids: list[str]
    enabled: bool


@dataclass(frozen=True)
class QueueSnapshot:
    durable: bool
    consumers: int
    messages: int


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
        matched = threading.Event()
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
                        matched.set()
                        ready.set()
            ready.set()  # Fail immediately on early process exit, not after the timeout.

        threading.Thread(target=record, daemon=True).start()
        if not ready.wait(100) or not matched.is_set() or process.poll() is not None:
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

    def wait_until(label: str, predicate: Callable[[], bool], timeout: int = 60) -> None:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if predicate():
                return
            threading.Event().wait(0.2)
        raise RuntimeError(f"Timed out waiting for {label}; see {base}")

    def queue_snapshot(label: str) -> QueueSnapshot | None:
        result = subprocess.run([str(artemis), "queue", "stat", "--json", "--silent", "--url", broker_url,
                                 "--user", env["ARTEMIS_USER"], "--password", env["ARTEMIS_PASSWORD"],
                                 "--field", "ADDRESS", "--operation", "EQUALS", "--value",
                                 "tracking.telemetry.locations"], capture_output=True, text=True, timeout=15)
        (base / f"{label}-queue.log").write_text(result.stdout + result.stderr)
        if result.returncode != 0:
            return None
        # Validate the external CLI reporting boundary and retain only its typed contract.
        report = json.loads(result.stdout)
        for row in report["data"]:
            if row["address"] == "tracking.telemetry.locations":
                return QueueSnapshot(row["durable"] == "true", int(row["consumerCount"]), int(row["messageCount"]))
        return None

    def has_consumer() -> bool:
        snapshot = queue_snapshot("consumer")
        return snapshot is not None and snapshot.durable and snapshot.consumers > 0

    def queue_retains_fixes() -> bool:
        snapshot = queue_snapshot("retained")
        return snapshot is not None and snapshot.durable and snapshot.consumers == 0 and snapshot.messages == 2

    def packet(when: datetime, latitude: str) -> str:
        return f"*HQ,8168000008,V1,{when:%H%M%S},A,{latitude},N,00006.0000,W,012.96,000,{when:%d%m%y},FBFBBFF,460,00,10342,4283,10,25,128#"

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
        # Apply only the checked-in telemetry policies to this owned broker instance.
        config_path = base / "broker/etc/broker.xml"
        config_text = config_path.read_text()
        if config_text.count("</address-settings>") != 1:
            raise RuntimeError("Owned broker has no address-settings section")
        # Keep Artemis' original unprefixed core/bootstrap elements: its boot parser
        # is sensitive to ElementTree's otherwise-equivalent ns0/ns1 rewriting.
        XML.register_namespace("", "urn:activemq:core")
        policies = "".join(XML.tostring(policy, encoding="unicode") for policy in
                           XML.parse(root / "docs/telemetry-broker-address-settings.xml").getroot())
        merged = config_text.replace("</address-settings>", policies + "</address-settings>")
        XML.fromstring(merged)
        config_path.write_text(merged)
        broker_command = [str(base / "broker/bin/artemis"), "run"]
        broker = start("broker", broker_command, "AMQ221007", env)
        backend_command = ["java", "-Xmx384m", "-jar", str(jars[0]), "--spring.profiles.active=postgres",
                          "--server.address=127.0.0.1", f"--server.port={java_port}",
                          f"--spring.data.redis.port={redis_port}", "--userauth.password.bcrypt-strength=4",
                          "--tracking.alert-outbox.poll-delay-ms=3600000"]
        backend = start("backend", backend_command, "Started TrackingEngineApplication", env)
        origin = f"http://127.0.0.1:{java_port}"

        def post(path: str, body: Registration | VehicleInput | TrackerInput | OverspeedRule, token: str | None = None) -> bytes:
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
            if label == "owner":
                post("/api/v1/alerts/rules/templates/overspeed", OverspeedRule(
                    "durable-speed", "Durable telemetry speed", 10, [vehicle], True), account.access_token)
        wait_until("initial durable intake consumer", has_consumer)
        # Both backend and broker go offline. Capture must depend on neither.
        stop(backend)
        stop(broker)
        spool = base / "device-spool"
        device_command = ["java", "-Xmx256m", "-jar", str(jars[1]), "--tracking.tcp.server.host=127.0.0.1",
                          f"--tracking.tcp.server.port={tcp_port}", f"--tracking.telemetry.spool.directory={spool}"]
        device = start("device-offline", device_command, "TCP Server started successfully", env)
        now = datetime.now(timezone.utc)
        frame = packet(now, "5130.0000")
        with socket.create_connection(("127.0.0.1", tcp_port), timeout=20) as peer:
            peer.sendall(frame.encode("ascii"))
            wait_until("fsynced captured fix", lambda: len(list(spool.glob("*.json"))) == 1)
        retained = next(spool.glob("*.json")).read_bytes()
        device.kill()  # Abrupt gateway crash, not merely a graceful shutdown.
        device.wait(timeout=20)
        device = start("device-restarted", device_command, "TCP Server started successfully", env)
        if next(spool.glob("*.json")).read_bytes() != retained:
            raise AssertionError("Gateway restart changed the retained envelope")
        with socket.create_connection(("127.0.0.1", tcp_port), timeout=20) as peer:
            peer.sendall(frame.encode("ascii"))  # Device-like resend has a different capture UUID.
            wait_until("second captured envelope", lambda: len(list(spool.glob("*.json"))) == 2)
        if sql("SELECT count(*) FROM tracker_location_data") != "0":
            raise AssertionError("Offline backend unexpectedly saved a location")
        broker = start("broker-recovered", broker_command, "AMQ221007", env)
        wait_until("durable broker retention without a consumer", queue_retains_fixes)
        wait_until("confirmed captures removed from local spool", lambda: not list(spool.glob("*.json")))
        # Now only the broker owns the messages. Its persistent journal must survive restart too.
        stop(broker)
        broker = start("broker-restarted", broker_command, "AMQ221007", env)
        wait_until("journal-retained fixes", queue_retains_fixes)
        if sql("SELECT count(*) FROM tracker_location_data") != "0":
            raise AssertionError("Backend-offline fixes were not retained at the transport boundary")
        backend = start("backend-recovered", backend_command, "Started TrackingEngineApplication", env)
        wait_until("one deduplicated persisted fix", lambda: sql("SELECT count(*) FROM tracker_location_data") == "1")
        wait_until("one persisted alert", lambda: sql("SELECT count(*) FROM tracker_alerts") == "1")
        if sql("SELECT count(*) FROM telemetry_receipts") != "1":
            raise AssertionError("Resent fix created duplicate receipts")
        # Historical replay stays in history but cannot rewind the live position or emit old alerts.
        with socket.create_connection(("127.0.0.1", tcp_port), timeout=20) as peer:
            peer.sendall(packet(now - timedelta(hours=1), "5129.0000").encode("ascii"))
        wait_until("retained historical fix", lambda: sql("SELECT count(*) FROM tracker_location_data") == "2")
        if sql("SELECT l.latitude FROM vehicles v JOIN tracker_location_data l ON l.location_id=v.current_location_id WHERE v.vehicle_id='tracking-map-vehicle'") != "51.5":
            raise AssertionError("Historical replay rewound current location")
        if sql("SELECT count(*) FROM tracker_alerts") != "1":
            raise AssertionError("Historical replay created an extra alert")
        # An online consumer with an unavailable database must retain queued work too.
        subprocess.run([str(args.pg_bin / "pg_ctl"), "-D", str(base / "pgdata"), "-m", "fast", "-w", "stop"], check=True)
        pg_started = False
        with socket.create_connection(("127.0.0.1", tcp_port), timeout=20) as peer:
            peer.sendall(packet(datetime.now(timezone.utc), "5130.3000").encode("ascii"))
        def queue_has_uncommitted_work() -> bool:
            snapshot = queue_snapshot("database-outage")
            return snapshot is not None and snapshot.durable and snapshot.messages >= 1
        wait_until("queued work during database outage", queue_has_uncommitted_work)
        subprocess.run([str(args.pg_bin / "pg_ctl"), "-D", str(base / "pgdata"), "-l", str(base / "postgres-recovered.log"),
                        "-o", f"-h 127.0.0.1 -p {pg_port} -k {base}", "-w", "start"], check=True)
        pg_started = True
        wait_until("location after database recovery", lambda: sql("SELECT count(*) FROM tracker_location_data") == "3")
        print("PASS: disk capture, abrupt gateway restart, backend/database outages, broker journal restart, deduplication and late-fix handling", flush=True)
        test_env = {**env, "TRACKING_TEST_JAVA_ORIGIN": origin, "TRACKING_TEST_EMAIL": "owner@telemetry-test.example",
                    "TRACKING_TEST_PASSWORD": "eight123", "TRACKING_TEST_POSTGRES_PORT": str(pg_port),
                    "TRACKING_TEST_TCP_PORT": str(tcp_port), "TRACKING_TEST_DURABILITY": "true",
                    "TRACKING_TEST_INITIAL_COORDINATES": "51.50500, -0.10000"}
        with (base / "browser.log").open("w") as output:
            subprocess.run(["pnpm", "exec", "playwright", "test", "--config", "playwright.tracking.config.ts"],
                           cwd=args.frontend, env=test_env, stdout=output, stderr=subprocess.STDOUT, check=True, timeout=180)
        print("PASS: recovered telemetry and duplicate-safe replay reach automatic browser refresh", flush=True)
    finally:
        for process in reversed(processes):
            stop(process)
        if pg_started:
            subprocess.run([str(args.pg_bin / "pg_ctl"), "-D", str(base / "pgdata"), "-m", "fast", "-w", "stop"], check=True)


if __name__ == "__main__":
    main()
