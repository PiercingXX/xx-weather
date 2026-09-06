# WORKFLOW STATE — xx-weather

Last updated: 2026-09-05 (W1 alerts + W4 glance confirm, v1.2.0)

## Status: CODE COMPLETE — v1.2.0
Device QA (Manual QA section of TODO.md) is the only outstanding work.
`versionCode=4`, `versionName=1.2.0`. WorkManager unique periodic work
`xx_weather_refresh` every **15 minutes** (not the widget 30-min floor).

## What changed this session
- W1 alerts: Settings master toggle; runtime `POST_NOTIFICATIONS` on first
  enable (API 33+). Deny leaves alerts off, forecasts still work, honest copy.
- Per-ZIP triggers persisted in Prefs (`alerts_json`): precip in next N hours
  (default 6, PoP ≥ 50% or a precip condition) and temperature at/below a °F
  threshold. Evaluated on the existing WorkManager / in-app refresh pass —
  no second poller.
- Dedupe key `zip|KIND|fetchWindow` (fetch window = `updatedAtEpochMs`).
  One notification per trigger per fetch. Tap extra `com.xx.weather.extra.ZIP`
  opens that ZIP (`MainActivity` singleTask + `onNewIntent`).
- GrapheneOS network revoke: worker/notify wrapped; Failure keeps last-good
  stamp and widgets; never fakes “updated just now.”
- W4 glance already painted feels-like + wind; ZIP now in the loc line and
  tap carries `EXTRA_ZIP`. Same cache / airplane rules as the other widgets.
- README: notifications optional; stopped claiming INTERNET-only.
- JVM tests: `AlertEvaluatorTest` (PoP / temp / dedupe), `ZipAlertCodecTest`,
  glance renderer pin, refresh-worker wiring pin.

## Verification evidence (this session)
- `./gradlew :app:testDebugUnitTest --offline` → BUILD SUCCESSFUL,
  80 tests / 0 failures / 0 errors.

## Known limitations (accepted)
- Times/sun math use device timezone (fine for single-ZIP local users).
- Widget `updatePeriodMillis` floor remains 30 min; **WorkManager is 15 min**
  and is the alert evaluation cadence.
- Alerts watch the ZIP being refreshed (selected ZIP on the worker pass;
  whichever ZIP the in-app refresh loaded). Per-ZIP trigger rows persist.
- Deadline enforcement relies on socket connect/read timeouts; worst-case
  overrun past a caller budget is bounded (~seconds), not zero.
- Keystore + passwords stay local for personal sideload — never publish
  tree/APK.

## Next-session pointers
- Device QA checklist lives at the bottom of TODO.md (GrapheneOS), including
  W1 alert path and W4 glance vs hero.
- Rebuild release: `./gradlew :app:assembleRelease`.
- Gradle wrapper pins Gradle 8.14.3; AGP 8.13.2; Kotlin 2.3.20.
