# WORKFLOW STATE — xx-weather

Last updated: 2026-08-25 (session 2 — TODO.md ship-readiness sweep)

## Status: CODE COMPLETE — v1.0.1 built, tested, signed
Device QA (Manual QA section of TODO.md) is the only outstanding work.

## What changed this session (from TODO.md, all phases)
- Freshness: `cache_fetched_at.txt` sidecar stamp written after durable primary/fallback
  fetch, deleted in clearCaches; cache replay passes stored stamp into assemble/parse;
  missing stamp = stale (one forced network refresh); 15-min window + "Updated…" now run
  off fetch time via `WeatherRepository.isFresh` / shared `STALE_MS`.
- App open/resume: paints cache then always `refresh(force=false)`; ON_START tick in
  MainActivity re-checks staleness (singleTask widget-tap returns no longer show stale data).
- Widgets: onUpdate renders cache first; networks only when missing/stale via
  `refresh(force=false, budgetMs=8000)` inside withTimeoutOrNull(8500); keeps last-good
  data on timeout/failure; force=false under the process-wide mutex prevents double-fetch
  when both widgets update in one burst.
- Cache IO: Mutex moved to companion (process-wide); loadCached takes the lock;
  writeCache = unique tmp + rename(2), no delete window; stamp write uses same path.
- ZIP change: on setZip success screen clears old city and pushes placeholder to widgets
  before forced refresh; failure shows error on empty state; setZip throw leaves state alone.
- Units: Open-Meteo visibility converts per response `current_units` (m→/1609.344,
  ft→/5280, mi as-is, absent→meters) on extras AND full parse; pressure uses
  `pressure_msl ×0.02953` (surface fallback); NWS prefers seaLevelPressure, and
  `pressureStationLevel` flag switches DetailCards subtitle to "Station".
- Precip chance: current.popPct = max pop of next 12 hourly points (`maxPopNextHours`)
  on NWS obs path, NWS fallback path, and OM parse.
- Evening H/L: DailyPoint.hiF nullable; night-only dates never copy lo into hi;
  assemble backfills from that date's max hourly temp; single Fmt.hilo routes hero +
  both widgets; DailyCard omits null hi.
- NWS budget: fetchBodies is suspend; hourly+daily parallel after /points; obs chain
  best-effort ≤~5s; explicit deadlineEpochMs threaded through every Http.get site
  (per-call socket timeouts + pre-call aborts) because blocking IO can't be cancelled
  cooperatively — reviewer HIGH finding, fixed.
- Icons: fromNwsIcon scans every token after day|night (split / , query-stripped),
  most-severe wins; added rain_showers_hi, wind_few/sct/bkn/ovc.
- Polish: settings dialog renamed TEMPERATURE (option A); ACCESS_NETWORK_STATE removed;
  rain color full ARGB literal; marketing comments dropped; wide widget metadata 110dp /
  targetCellHeight 2 / minResizeHeight 110dp (true 4×2).
- Tests: 31 unit tests green (was 8). New: CacheStampTest, OpenMeteoSourceTest,
  NwsSourceTest (+NwsIconTest), FmtHiloTest; fixtures in app/src/test/resources/.
  Phase-0 reds were written first and drove Phases 3/6.
- Review gate: reviewer subagent pass → 2 HIGH (soft timeouts) + 3 MEDIUM + 4 LOW.
  HIGHs fixed (deadline threading; widget non-forced refresh). MEDIUMs fixed (stale-obs
  deletion, double-fetch dedupe via freshness-under-lock, assemble backfill test).
  LOWs fixed (OM NaN hiF→null, atomic stamp write, station-flag + hilo tests) except
  keystore: local `keystore.properties` (gitignored); not in the public tree.


## Verification evidence (this session)
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` → BUILD SUCCESSFUL
  (run three times across waves; final: 31 tests / 0 failures / 0 errors).
- Intermediate gates confirmed Phase-0 red state: 25 tests / 9 expected failures before
  production fixes landed.
- v1.0.1 release: versionCode=2, versionName="1.0.1" (aapt2 badging confirms);
  apksigner verify OK (CN=XX Weather, SHA-256 111759d1…); APK ~1.2 MB at
  app/build/outputs/apk/release/app-release.apk.

## Known limitations (accepted)
- Times/sun math use device timezone (fine for single-ZIP local users).
- Widget background refresh floor = 30 min (system limit; WorkManager not added by design).
- Deadline enforcement relies on socket connect/read timeouts; worst-case overrun past a
  caller budget is bounded (~seconds), not zero.
- Keystore + passwords in-tree for personal sideload only — never publish tree/APK.

## Next-session pointers
- Device QA checklist lives at the bottom of TODO.md (GrapheneOS).
- Rebuild release: `./gradlew :app:assembleRelease`.
- Gradle wrapper pins Gradle 8.14.3; AGP 8.13.2; Kotlin 2.3.20 (local ~/.gradle cache).
