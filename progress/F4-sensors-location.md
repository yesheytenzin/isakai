---
id: "F4"
status: "deferred"
phase: "DEFERRED"
priority: "P2"
size: "M"
updated: "2026-10-14"
owner: "unassigned"
next: "no action while deferred; revisit only if the task is un-deferred"
---

# F4: Expose honest sensors and location capabilities

## Dependencies

None.

## Code and documentation

- [framework/org/isakai/bridge/IsakaiSensor.java](../framework/org/isakai/bridge/IsakaiSensor.java)
- [framework/org/isakai/bridge/IsakaiLocation.java](../framework/org/isakai/bridge/IsakaiLocation.java)

## Delivery review details

Real provider discovery through `IsakaiSensor`, `IsakaiLocation` and authenticated desktop broker.

**Additional acceptance / stop gate:** Denial/unavailable/cancellation and real host values; no raw user session-bus or broad device grants.

**Original planning estimate:** 1–3 weeks per selected provider family.


## Acceptance

No fixed location is presented as a real reading; unavailable providers are reported; enabled backend reads reflect the host and stop after cancellation.

## Verification evidence

### 2026-10-12 — partial

Fabricated production readings removed: without a host provider (and without the explicit test switch ISAKAI_FAKE_SENSORS/ISAKAI_FAKE_LOCATION or -Disakai.fakeSensors/-Disakai.fakeLocation), SensorManager advertises no sensor, registerListener returns false, LocationManager reports providers disabled and getLastKnownLocation returns null. A listener removed before its queued delivery now receives nothing (location registration tokens; sensor registrations are marked inactive before removal). Regression: android.hardware.SensorLocationTest covers both modes plus the cancellation paths. Real provider discovery (iio-sensor-proxy/GeoClue2 through the bridge) is still unwired.

Artifacts:

- `/tmp/isakai-checks-final/java.log`

Command:

```sh
zig build test-java
```


## Code review

Not recorded.
