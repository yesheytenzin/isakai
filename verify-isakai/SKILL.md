# Verify Isakai by testing the UI

There are no automated test suites. Verification means launching real apps
as real windows, driving the visible task, and recording screenshots, logs
and exit status as evidence in [progress.json](../progress.json), the only
progress tracker.

## Build

From the repository root, run `zig build`. Use Zig 0.17 and a JDK 17+
compiler; host system libraries are listed in
[host requirements](../docs/HOST_REQUIREMENTS.md).

## UI flows

| Flow | Command | Proves |
|---|---|---|
| Isolated pinned-app flow | `python3 verify-isakai/sway-flow.py {opencalc\|calculator\|notes} --work <dir>` | OpenCalc calculates/copies/resizes and stays alive; Calculator reaches settings, persists the preference and returns via back; Notes edits a Unicode note, saves it and returns to the list. Retains screenshots and app logs under `--evidence`. |
| Live-compositor drive | `python3 verify-isakai/flow-drive.py <opencalc\|calculator\|notes> [--work DIR]` | Same flows under the live compositor with per-action focus guards. Needs a workspace not shared with interactive work. |
| App observer | `python3 verify-isakai/observe-app.py <0\|1\|2> --work <dir>` | Startup screenshots and failures; does not establish useful interaction. |
| Live window | `python3 verify-isakai/window-flow.py --unprivileged --required` | Real fixture stays open >30 seconds, accepts text, closes through compositor and back, records exit zero and removes data. |
| Full privileged flow | `tools/e2e-privileged.sh --required` | Fresh fixture and peer app install; app-side UID/GID, empty groups, NNP, seccomp, distinct mount namespace and filesystem assertions pass; real surface/input/lifetime/close/back pass; users/data removed. |

Without prerequisites, the privileged script returns 77 with SKIP; `--required`
or `ISAKAI_E2E_REQUIRED=1` turns that into exit 1. Build failures, missing
executables, subprocess failures and assertion failures are errors, never
skips. The privileged entry point rejects `--unprivileged`.

The isolated target-app flow requires the private Sway fixture and
`isakai-vinput` built by the repository's compositor fixture. Start that
fixture before invoking `sway-flow.py`; a missing compositor is an environment
failure, not an app-flow pass or skip.

Inspect `opened-*.png` and `typed-*.png` for the fixture text and typed RUNTIME
suffix, and `isolation.log` for app-side results. Record paths and outcomes in
[progress.json](../progress.json). A same-UID pass must not be reported as a
privileged pass. A window screenshot alone is insufficient: evidence must tie
screenshots to asserted actions and saved results.

## Platform references

- [Linux Landlock UAPI](https://docs.kernel.org/userspace-api/landlock.html): version query, ruleset creation, ABI rights and inheritance.
- [polkit action declarations](https://polkit.pages.freedesktop.org/polkit/polkit.8.html): multiple actions per namespace policy file.
- [Hyprland dispatchers](https://wiki.hypr.land/configuring/core/dispatchers/): version-aware focus and graceful close of the exact driven window.
