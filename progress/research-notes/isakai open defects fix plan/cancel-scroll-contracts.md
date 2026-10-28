# File-picker cancel propagation & scrolled-coordinate reporting contracts

## startActivityForResult → picker cancel → onActivityResult chain

### Takeaway
A cancel must travel picker → result dispatcher → `onActivityResult(requestCode, RESULT_CANCELED, null)` with the original `requestCode` intact; any layer that swallows the cancel (or the request code) leaves the caller hanging, and the modern replacement is the callback-based Activity Result API rather than a blocking call.

### Cited Findings
- `startActivityForResult(intent, requestCode)`: "If >= 0, this code will be returned in `onActivityResult()` when the activity exits." — [HostActivity reference](https://developer.android.com/reference/kotlin/androidx/media3/test/utils/HostActivity)
- `startActivityForResult` is "deprecated in favor of using the Activity Result API which brings increased type safety via an `ActivityResultContract` and the prebuilt contracts for common intents available in `androidx.activity.result.contract.ActivityResultContracts`" — [DynamicNavHostFragment reference](https://developer.android.com/reference/kotlin/androidx/navigation/dynamicfeatures/fragment/DynamicNavHostFragment)
- Deny/error maps to cancel: "If Bluetooth was not enabled due to an error (or the user responded 'Deny') then the result code is `RESULT_CANCELED`" — [Bluetooth setup](https://developer.android.com/develop/connectivity/bluetooth/setup); sample code guards with `if (resultCode != Activity.RESULT_OK) { return; }` — [Companion device pairing](https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing)
- Result constants mirrored in-repo: `RESULT_OK = -1`, `RESULT_CANCELED = 0` (`framework/android/app/Activity.java:29-32`, read-only mirror)
- In-repo bridge hand-offs for the cancel path (`framework/android/app/Activity.java`, read-only mirror): `startActivityForResult` (line 267) opens the desktop picker via `IsakaiDesktop.openFile` and stores `pendingFileRequestCode`/`pendingFileReq`; `pollDesktopResults` (line 350) drains the `IsakaiDesktop.poll` batch and for `kind == 12` file results delivers `onActivityResult(code, RESULT_OK, data)` on `status == 1` and `onActivityResult(code, RESULT_CANCELED, null)` on `status == -1`; component intents route via `navigator.launch(this, intent, requestCode)`; activity-stack results deliver via `ended.resultParent.onActivityResult(ended.requestCode, ended.resultCode, ended.resultData)` (line 605); the no-bridge save fallback is fail-closed — "when creation fails, deliver RESULT_CANCELED" (`deliverSaveFallback`, line ~310)
- `GetContent()` contract callback "is invoked once the user selects an image and returns to the launching activity" — async delivery, no blocking — [Compose libraries](https://developer.android.com/develop/ui/compose/libraries)

### Inferences
- Every hand-off that must preserve the cancel: (a) picker dismissal must emit a `status == -1` event (not just close the dialog silently); (b) `pollDesktopResults` must match `req == pendingFileReq` and must not `closeReq` without delivering; (c) the stored `pendingFileRequestCode` must be delivered unchanged; (d) `navigator`/activity-stack paths must forward `resultCode`/`requestCode` untouched. Dropping any one means `onActivityResult(..., RESULT_CANCELED, null)` never fires and a caller awaiting the result stalls.
- Note the `startActivity` picker delegation in-repo passes request code `-1`, which by the "If >= 0" contract means no result is owed on that path — cancel propagation only applies to the `startActivityForResult` path with a non-negative code.

### Gaps
- I did not fetch AOSP `ActivityManager`/`Instrumentation` internals, so the system-side leg (picker `setResult`/`finish` → AMS → caller `onActivityResult` dispatch) is not directly quoted here; verify at `platform/frameworks/base/core/java/android/app/Activity.java` and `Instrumentation.java` on cs.android.com.

## Polling vs blocking result APIs

### Takeaway
There is no production blocking/wait-for-result API in the activity-result contract — delivery is callback-based (`onActivityResult` or launcher lambda); the only blocking query found is a test-only API, and this repo resolves picker results by per-frame polling.

### Cited Findings
- The only blocking result API found is test-only: `ActivityScenario.getResult()` "Waits for the activity to be finished and returns the activity result. ActivityScenario.launchActivityForResult() must be used to launch an Activity before this method is called… The activity must be finishing or finished otherwise this method will throws runtime exception after the timeout." — [ActivityScenario reference](https://developer.android.com/reference/androidx/test/core/app/ActivityScenario)
- Production delivery is a callback: the `rememberLauncherForActivityResult` trailing lambda "is invoked once the user selects an image and returns to the launching activity" — [Compose libraries](https://developer.android.com/develop/ui/compose/libraries)
- This repo's norm is per-frame polling: the `runWindowed` event loop calls `window.run(100)`, drains input, then calls `active.pollDesktopResults()` every iteration (`framework/android/app/Activity.java:1047-1063`, read-only mirror)

### Inferences
- Per-frame polling of the desktop event queue is the established in-repo pattern for result delivery; introducing a blocking wait would diverge from both the platform contract and the repo loop. A cancel that arrives between frames is therefore inherently latent by up to one frame — acceptable, but the poll must run unconditionally each frame, not only when input is present.

### Gaps
- No source was found for any production `ContentResolver`/picker query API that blocks until a result arrives; if such an API is suspected, it needs a dedicated search (none is part of the documented `GetContent`/`OpenDocument` contract surface found here).

## getLocationInWindow vs getLocationOnScreen and scroll offsets

### Takeaway
`getLocationInWindow` reports window coordinates and `getLocationOnScreen` reports screen coordinates (they differ under letterboxing/cutout); per the official view-geometry contract, location accumulates relative to parents — and the in-repo `View.getLocationInWindow` sums `left`/`top` up the chain with no ancestor-scroll adjustment, while `getLocationOnScreen` is a plain alias, which disagrees with expected scrolled-container reporting.

### Cited Findings
- `getLocationInWindow`: "Gets the location of the view in window coordinates." `getLocationOnScreen`: "Gets the location of the view on the screen." — [AbstractComposeView reference (inherited View contract)](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/AbstractComposeView)
- "Use `View.getLocationInWindow()` to determine how much window space your app is using… don't use `View.getLocationOnScreen()`" and "In immersive mode, be careful using window versus screen coordinates, since your app doesn't use the whole screen when letterboxed… coordinates from the screen origin aren't the same as coordinates from the window origin." — [Display cutout support](https://developer.android.com/develop/ui/views/layout/display-cutout)
- `getLeft()`/`getTop()` "return the location of the view relative to its parent" — [Declaring layout](https://developer.android.com/develop/ui/views/layout/declaring-layout)
- In-repo implementation (`framework/android/view/View.java:1774`, read-only mirror) walks the parent chain summing `p.left`/`p.top` only — no subtraction of any ancestor `scrollX`/`scrollY`; and `getLocationOnScreen` (line 700) is literally `{ getLocationInWindow(out); }` with no window-to-screen offset added.

### Inferences
- For a view inside a scrolled container, the on-screen/window position must reflect ancestor scroll (content shifts by −scroll), so a `getLocationInWindow` that ignores ancestor `scrollX`/`scrollY` over-reports the position of scrolled content — matching the reported defect. The fix direction is to subtract each ancestor's scroll during the walk (and, for `getLocationOnScreen`, add the window-to-screen offset), but the exact AOSP formulation was not fetched (see Gaps).

### Gaps
- The exact AOSP `View.getLocationInWindow` body (matrix handling, per-ancestor `mScrollX`/`mScrollY` subtraction, attach-info/window offset) was not retrieved — Context7 returned only the one-line reference definitions. Verify directly at `platform/frameworks/base/core/java/android/view/View.java` (`getLocationInWindow`, `getLocationOnScreen`, `getLocationInSurface`) on cs.android.com before finalizing the fix math.

## ViewGroup touch-transform and draw scroll math

### Takeaway
AOSP's touch hand-off into a child uses `offsetX = mScrollX − child.mLeft` (scroll-aware), and scrolled drawing paints content shifted by −scroll; the repo's `ViewGroup` mirrors exactly this math in both `dispatchTouchEvent` and `dispatchDrawAt`.

### Cited Findings
- In-repo touch math (`framework/android/view/ViewGroup.java:523-560`, read-only mirror), with an explicit AOSP-parity comment: "touch coordinates are in the scrolled content frame… `contentX = eventXY − groupXY + groupScroll` (`ViewGroup.dispatchTransformedTouchEvent` uses `offsetX = mScrollX − child.mLeft`)"; code computes `localX = event.getX() − getLeft() + scrollX` and descends with `event.offsetLocation(−getLeft() + scrollX, −getTop() + scrollY)`, restoring after.
- In-repo draw math (`dispatchDrawAt`, lines 766-779, read-only mirror), with AOSP-parity comment: "scrolled content paints shifted by −scroll"; code computes `baseX = offsetX − getScrollX()`, `baseY = offsetY − getScrollY()` and paints each child via `drawAt(canvas, baseX, baseY)`.

### Inferences
- Touch and draw in-repo are mutually consistent (both add back / subtract the group's scroll), so hit-testing agrees with what is displayed — provided `getScrollX/Y` report the real scroll and children report scroll-correct geometry via `getLocationInWindow` (see previous section for the remaining gap). Any residual mis-hit in scrolled containers therefore points at the location-reporting side, not the dispatch/draw offsets.

### Gaps
- AOSP `ViewGroup.dispatchTransformedTouchEvent` / `drawChild` bodies were not fetched (no Context7 match on `source_android_core`); the `offsetX = mScrollX − child.mLeft` formula is attested only via the repo's parity comment. Confirm verbatim at `platform/frameworks/base/core/java/android/view/ViewGroup.java` on cs.android.com.
