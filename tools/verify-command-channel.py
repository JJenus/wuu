#!/usr/bin/env python3
"""Owned Redis + packaged device gateway + backend channel + real TCP acceptance; never deploys."""
import os
from pathlib import Path
import secrets
import signal
import socket
import subprocess
import tempfile
import threading
from types import FrameType


def free_port() -> int:
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    jar = root / "device-comm/target/device-comm-1.0.0-SNAPSHOT.jar"
    if not jar.is_file():
        raise RuntimeError("Package device-comm first")
    base = Path(tempfile.mkdtemp(prefix="command-channel-native-", dir="/tmp/opencode"))
    print(f"Owned command-channel evidence: {base}", flush=True)
    redis_port, tcp_port, gateway_port, unused_broker_port = [free_port() for _ in range(4)]
    env = {key: value for key, value in os.environ.items() if key in {"PATH", "HOME", "JAVA_HOME", "LANG"}}
    env.update({"REDIS_HOST": "127.0.0.1", "REDIS_PORT": str(redis_port),
                "COMMAND_GATEWAY_ENABLED": "true", "COMMAND_GATEWAY_HOST": "127.0.0.1",
                "COMMAND_GATEWAY_PORT": str(gateway_port), "COMMAND_CHANNEL_SECRET": secrets.token_hex(32),
                "COMMAND_WRITE_JOURNAL_DIRECTORY": str(base / "write-journal"),
                "COMMAND_GATEWAY_ORIGIN": f"http://127.0.0.1:{gateway_port}",
                "COMMAND_NATIVE_TCP_PORT": str(tcp_port),
                "ARTEMIS_URL": f"tcp://127.0.0.1:{unused_broker_port}?initialConnectAttempts=0&reconnectAttempts=0&callTimeout=1000"})
    children: list[subprocess.Popen[str]] = []

    def start(label: str, command: list[str], markers: list[str]) -> None:
        process = subprocess.Popen(command, cwd=base, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        children.append(process)
        ready, matched = threading.Event(), threading.Event()

        def record() -> None:
            seen: set[str] = set()
            with (base / f"{label}.log").open("w") as output:
                assert process.stdout is not None
                for line in process.stdout:
                    output.write(line); output.flush()
                    seen.update(marker for marker in markers if marker in line)
                    if len(seen) == len(markers):
                        matched.set(); ready.set()
            ready.set()
        threading.Thread(target=record, daemon=True).start()
        if not ready.wait(90) or not matched.is_set() or process.poll() is not None:
            raise RuntimeError(f"{label} failed to start; see {base}")

    def interrupted(signum: int, _frame: FrameType | None) -> None:
        raise SystemExit(128 + signum)
    signal.signal(signal.SIGTERM, interrupted)
    try:
        start("redis", ["redis-server", "--bind", "127.0.0.1", "--port", str(redis_port), "--save", "", "--appendonly", "no"], ["Ready to accept connections"])
        start("gateway", ["java", "-Xmx256m", "-jar", str(jar), "--tracking.tcp.server.host=127.0.0.1",
                          f"--tracking.tcp.server.port={tcp_port}", f"--tracking.telemetry.spool.directory={base / 'telemetry-spool'}"],
              ["TCP Server started successfully", "Command gateway listening"])
        with (base / "backend-channel.log").open("w") as output:
            subprocess.run([str(root / "mvnw"), "-B", "-Dtest=CommandChannelNativeIT", "test"], cwd=root,
                           env=env, stdout=output, stderr=subprocess.STDOUT, check=True, timeout=180)
        print("PASS: backend dispatcher/client -> authenticated independent gateway -> exact binary TCP bytes; duplicate/conflict/expiry handling and reconnect-safe routing", flush=True)
    finally:
        for process in reversed(children):
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    process.kill(); process.wait()


if __name__ == "__main__":
    main()
