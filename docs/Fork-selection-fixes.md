# Windows selection and startup fixes

Base: upstream `develop`, 7135d26b44d4fd57db4982313f7e281544d7ba5f.

## Evidence and scope

The automatic selection path accepts only a left-button drag. Double-click and keyboard
selection therefore never request capture. The supplied Windows log also records
`Clipboard change monitor unavailable, skipping selection capture`; manual copying can
make capture work. The original implementation did not distinguish a native call failure from
a zero counter; diagnostics now report the safe reason without clipboard data or exception text.
The user additionally reports that the Run-key entry fails to launch while an interactive
Task Scheduler login task with highest privileges works. That startup report is not yet
reproduced on Windows.

## Implementation plan and invariants

1. Extend selection gestures to double/triple clicks, with monotonic time and distance
   bounds. Detect Windows Shift+navigation and Ctrl+A without registering/consuming those
   ordinary editing shortcuts. Coalesce automatic captures and cancel stale requests on
   subsequent user input, configuration changes, or shutdown. Ignore injected events.
2. Retain native clipboard generations when available. If the initial native observation is
   unavailable, use a content-signature monitor for that entire capture transaction. Retry
   the native monitor on the next transaction; never compare tokens across monitors, accept
   old clipboard text without an observed change, or publish sentinel clipboard contents.
3. Add per-user interactive login-task registration with an explicit executable, arguments,
   working directory and no password. Prefer highest privileges only when the running user
   can register that task; preserve an existing Highest task. Normalize account names to SIDs
   when checking idempotence. Use the existing Run-key fallback only after proving no task exists;
   an unreadable scheduler or failed update must preserve the existing task and report failure.
   Disable removes app-owned registrations only. Report failures rather than claiming success.

Existing selection OFF remains OFF, manual hotkeys remain available, and no data/schema or
provider changes are required. Mouse gestures remain portable; new native-key interpretation
is explicitly Windows-only. Other platforms keep their current keyboard-selection behavior.
Rollback is switching back to the previous build; disable autostart in this build before doing so.

## Verification

Deterministic regressions: double/triple click timing and spatial bounds; Shift navigation,
Ctrl+A, typing/injection exclusions; latest-capture cancellation; fallback monitor failure,
recovery, unchanged text and clipboard restoration; scheduled-task arguments, per-user scope,
idempotence, enable/disable and failure behavior. Run focused suites, owning module suites,
then the canonical build. Two independent reviews cover input/cancellation and startup/privacy.
Real Windows login, UAC, native clipboard and multi-monitor checks must be reported separately
from headless tests; macOS/X11 keyboard additions and Wayland raw observation are out of scope.

## Review corrections

Tracker state and resets share a short lock; no native calls or UI callbacks run under it.
An event carries its capture epoch across pointer/focus callbacks, and queued Swing delivery
validates the epoch again on the EDT. This prevents a disabled/re-enabled selection or later input
from resurrecting an old result. Multi-click captures wait for the click chain to settle.

A failed signature read is unavailable, never a signature of missing text. Cancellation before
Copy performs no restoration; cancellation after Copy retains shielded restoration. Native failure
during an existing capture fails closed, with observer selection retried on the next capture.

Two independent audits covered input/concurrency and startup/clipboard privacy. Their findings
were resolved and the final source reviews reported no remaining findings. Deterministic tests
include gated lifecycle races, deferred UI delivery, actual AWT Transferable failures, and generated
PowerShell executed with stubbed scheduler mutations on Windows. These tests do not prove a real
sign-out/sign-in, elevation consent or physical keyboard input on the user's installed copy.

Fork build identity: `1.5.0-saltek.2`. Runtime/About and release validation use the same version.
An elevated application can create the Highest task; an ordinary application creates a Limited
interactive task and preserves an already configured matching Highest task. Windows does not allow
an ordinary process to silently grant itself elevated startup rights.

The General settings page also offers Start minimized beside autostart. This separate preference
defaults to false and applies to manual launches as well. Schema v10 preserves existing settings
and explicit choices. Apply/Cancel retain the usual draft behavior. Initial presentation waits for
actual tray installation and only shows the window when needed; it never hides an explicit restore
request that arrived while initialization was queued. Tests cover migration, persistence, checkbox
placement and draft behavior, failed tray installation, and early restore ordering without desktop
automation.
