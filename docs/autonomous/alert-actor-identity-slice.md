# Alert actor identity

STATUS=IN_PROGRESS

Single and bulk acknowledge/resolve derive audit actor identity from the
authenticated principal's database user ID, not client input. Existing
`alerts.write`, tenant scoping and resolution-note validation remain unchanged.
Legacy body actor fields are accepted but overwritten; bulk actor query inputs
are ignored. No frontend mutation or production deployment is claimed.

RED commit `b6be4ef` reproduced four failures in 22 security-boundary tests.
Before the environment restart, focused verification passed 32 tests and the
full disposable gate passed 735 backend / 91 device tests, zero failures/errors/
skips. Independent Java review approved with no concrete regression; its extra
build attempt timed out and is not independent build evidence.

The restart erased the isolated checkout, artifacts and `/tmp/opencode` receipts.
The uncommitted three-file fix was reconstructed from the preserved conversation
diff atop `b6be4ef` in `fix/alert-actor-recovery`. Historical results above are
conversation evidence only, not retained logs or a fresh reconstructed-tree gate.
Reconstructed GREEN `9c98b31` passed a fresh focused gate on 2026-10-30:
32 tests, zero failures/errors/skips; BUILD SUCCESS in 46.897 seconds.
Command: `./mvnw -B -Dtest=SecurityBoundaryIntegrationTest,AlertControllerTest,AlertServiceTest test`.
Receipt `/tmp/opencode/alert-actor-recovery-focused.log`. The completed full
backend/device gate was not repeated. No fresh coverage claim.

Do not repeat the completed product slices. Next: publish the reconstructed fix,
then implement narrowly allowlisted live alert transitions with
actual-Java mutation acceptance. Payment/provider/hardware/release gates remain.
