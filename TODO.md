# TODO — XX Weather ship-readiness

Fix list from the v1.0 review. The app looks complete; freshness, a few unit conversions, and widget sizing are not. Work top to bottom. Check a box only after the code change **and** its tests (or the manual check in that item) are done.

**Status (2026-08-25): all code phases implemented and gated green (`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` -> BUILD SUCCESSFUL, 31 tests / 0 failures; release APK v1.0.1 signed + apksigner-verified). The Manual QA section below still requires the GrapheneOS device.**

**Do not treat `WORKFLOW_STATE.md` as truth.** It claims the high findings were already fixed. They are not.

## Done when

- Opening the app after 15+ minutes hits the network without tapping Refresh.
- The “Updated …” stamp is the fetch time, not parse time.
- Home-screen widgets refresh on the 30-minute tick (or show last-good data, never a lying clock).
- Visibility and sea-level pressure are in the right units on both NWS and Open-Meteo paths.
- Precip-chance tile and evening Today H/L are honest.
- Failed ZIP change does not leave the old city on screen.
- Wide widget is actually 4×2 and does not clip the hourly strip.
- `./gradlew :app:testDebugUnitTest :app:assembleRelease` is green.

Personal GrapheneOS sideload is the v1.0 target. Public/F-Droid is Phase 9 and is optional.

---

## Phase 0 — failing tests first

Add fixtures and assertions **before** the production fixes so the bugs cannot silently return.

- [x] **0.1 Cache timestamp contract**
  - Extract a pure helper (e.g. `CacheStamp`) or pass `fetchedAtEpochMs` into `NwsSource.assemble` / `OpenMeteoSource.parse` so JVM tests can pin it.
  - Test: assemble/parse with a fixed `nowEpochMs` round-trips that value onto `WeatherData.updatedAtEpochMs`.
  - Test: “fresh” means `now - fetchedAt < 15 min`; a stamp 16 min old is stale.
  - Files: `WeatherRepository.kt`, `NwsSource.kt`, `OpenMeteoSource.kt`, new test class.

- [x] **0.2 Open-Meteo extras units**
  - Fixture A: `current_units.visibility = "m"`, `visibility = 57600` → **35.8 mi**, not 10.9.
  - Fixture B: `current_units.visibility = "ft"`, `visibility = 188976` → **35.8 mi**.
  - Fixture C: `pressure_msl = 1013.4`, `surface_pressure = 846.5` → **~29.93 inHg**, not ~25.00.
  - Files: `OpenMeteoSource.kt`, `LogicTests.kt` (or `OpenMeteoSourceTest.kt`).

- [x] **0.3 NWS obs + daily**
  - Fixture: METAR obs with `popPct` absent + hourly pops → current precip chance is max of next 12 hours, not null.
  - Fixture: first period `isDaytime=false` only (Tonight 61 F) + later daytime 88 F on the next date → **Today hi is not 61**.
  - Fixture: `seaLevelPressure` vs `barometricPressure` — prefer MSL.
  - Files: `NwsSource.kt`, new `NwsSourceTest.kt`.

- [x] **0.4 Dual-condition icons**
  - `…/icons/land/day/sct/tsra_hi,40` → `THUNDERSTORM`, not `PARTLY_CLOUDY`.
  - `rain_showers_hi` maps to `SHOWERS`.
  - Files: `Models.kt` (`Conditions.fromNwsIcon`), `LogicTests.kt`.

---

## Phase 1 — freshness (ship blockers)

These two bugs make the whole update pipeline a no-op after the first successful fetch.

### 1. Persist fetch time; stop stamping cache with `now`

- [x] **1.1 Stop defaulting `nowEpochMs = System.currentTimeMillis()` on cache replay**
  - `NwsSource.assemble` and `OpenMeteoSource.parse` already take `nowEpochMs`. Callers that **load cache** must pass the stored fetch time, not wall clock.
  - Files: `NwsSource.kt:71`, `OpenMeteoSource.kt:84`, `WeatherRepository.kt:123`.

- [x] **1.2 Write a fetch stamp when cache is written**
  - After a successful NWS or Open-Meteo write, persist `fetchedAtEpochMs` (sidecar file e.g. `cache_fetched_at.txt`, or a field in `cache_extras.json`, or `File.lastModified()` of the primary body **read at load**).
  - Prefer an explicit sidecar. `lastModified()` is easy to clobber and is not set on a bundle of files.
  - Clear the stamp in `clearCaches()`.
  - Old installs with cache but no stamp: treat as **stale** (force network once).
  - Files: `WeatherRepository.kt` (`writeCache` / `loadCachedBlocking` / `clearCaches` / `refreshLocked`).

- [x] **1.3 Drive `STALE_MS` and the UI clock off that stamp**
  - `refreshLocked(!force)` uses `now - fetchedAt < STALE_MS` (15 min).
  - `WeatherData.updatedAtEpochMs` is the stamp, so header/widget “Updated …” is fetch time.
  - Files: `WeatherRepository.kt:156-162`, `Fmt.updatedLabel`, widgets.

- [x] **1.4 App open must network when stale**
  - `WeatherScreen` `LaunchedEffect(Unit)` currently does `refresh(force = cached == null)`, so a cache hit never networks.
  - Change to: paint cache immediately, then `refresh(force = false)` so the stale window can fire.
  - Also refresh on **resume** (`Lifecycle.ON_START` / `ON_RESUME`), not only first composition. `singleTask` + widget tap otherwise shows yesterday’s data until the user hits Refresh.
  - Files: `WeatherScreen.kt:98-108`, `MainActivity.kt` if a lifecycle owner is cleaner than a Compose effect.

Manual check: fetch once, kill the app, reopen within 15 min → no network (or 304-equivalent cache hit). Wait 16 min, reopen → network, stamp moves.

### 2. Widgets must refresh when cache is stale

- [x] **2.1 `onUpdate` always considers staleness**
  - Today: load cache; network **only if `data == null`**. After the first write, 30-minute `APPWIDGET_UPDATE` just redraws the old body with a new clock.
  - Wanted:
    1. Render cache immediately if present (never flash “Tap to set ZIP” when a ZIP is saved).
    2. If cache missing **or stale**, `refresh(force = true)` inside the existing `withTimeoutOrNull(8_500)`.
    3. On timeout/failure, **keep last-good cache**. Do not replace it with the placeholder.
  - Files: `WeatherWidgets.kt:153-181`.

- [x] **2.2 Same `STALE_MS` as the app**
  - Do not invent a second freshness constant. If the repository exposes `isFresh(data)` / `refresh(force = false)`, use that.

Manual check: add both widgets, leave the app closed > 30 min, confirm hourly/current actually change (not just the “Updated” label).

---

## Phase 2 — cache IO is process-wide

### 3. One lock, atomic replace, load under the lock

- [x] **3.1 Process-wide mutex**
  - `ioMutex` is an instance field. UI `remember { WeatherRepository(appContext) }` and widget `WeatherRepository(applicationContext)` do not share it.
  - Move `Mutex` to `WeatherRepository` companion (or a singleton holder). Comment should say **why** (cross-instance cache files), not the architecture story.
  - Files: `WeatherRepository.kt:30`, `MainActivity.kt:19`, `WeatherWidgets.kt:161`.

- [x] **3.2 `loadCached()` takes the same lock**
  - Today only `refresh` / `setZip` lock. A ZIP clear can run while a reader is mid-pair.
  - Files: `WeatherRepository.kt:142-144`.

- [x] **3.3 Atomic replace, no delete window**
  - `writeCache` currently `write tmp` → `target.delete()` → `tmp.renameTo(target)`. The delete is a miss window.
  - Write unique tmp (`$name.tmp`) then `tmp.renameTo(target)` over the existing file (Linux/Android replace is atomic). Unique tmp names if anything can still run unlocked during the changeover.
  - Files: `WeatherRepository.kt:47-58`.

---

## Phase 3 — numbers on screen are wrong

### 4. Open-Meteo visibility and pressure

- [x] **4.1 Never hard-code `/ 5280`**
  - `fetchExtras` requests `temperature_unit=fahrenheit` but **not** `precipitation_unit=inch`. Live responses then send `visibility` in **meters**. Dividing meters by 5280 under-reports by ~3×.
  - `parse()` looks right only because the full-forecast URL sets `precipitation_unit=inch`, which (today) yields feet. That is a coincidence, not a contract.
  - Convert from `current_units.visibility`:
    - `m` / `meter` / `metres` → `/ 1609.344`
    - `ft` / `feet` → `/ 5280`
    - already `mi` → as-is
  - Same helper for extras **and** full parse.
  - Files: `OpenMeteoSource.kt:76`, `OpenMeteoSource.kt:174`.

- [x] **4.2 Align extras query with the full forecast**
  - Request `pressure_msl` (not only `surface_pressure`). Keep `temperature_unit=fahrenheit`. Optionally set `precipitation_unit=inch` for consistency, but **still** read `current_units`.
  - Files: `OpenMeteoSource.kt:45-52`.

- [x] **4.3 Sea-level pressure, labeled honestly**
  - Open-Meteo: use `pressure_msl` × 0.02953 → inHg. Station `surface_pressure` at elevation (Denver ~846 hPa) looks like a crashing barometer when labeled “Sea level”.
  - NWS: prefer `seaLevelPressure`, then `barometricPressure`. If only station pressure is present, change the subtitle from “Sea level” to “Station”.
  - Files: `OpenMeteoSource.kt:77`, `NwsSource.kt:178`, `DetailCards.kt:58-61`.

### 5. Precip-chance tile

- [x] **5.1 Fill `current.popPct` from the next 12 hourly points**
  - NWS METAR sets `popPct = null`. `mergeExtras` does not touch it. The tile is `--` on the common happy path. PoP is already on `hourly`.
  - After assemble/parse: `current.popPct = max of hourly.popPct in the next 12 hours` (skip nulls). If none, leave null.
  - Subtitle “Next 12 hours” must match the math. Do **not** use only `hourly.first()`.
  - Same helper for NWS assemble, NWS fallback current, and Open-Meteo parse.
  - Files: `NwsSource.kt:75`, `NwsSource.kt:202-215`, `OpenMeteoSource.kt:177`, `DetailCards.kt:73-76`.

### 6. Evening Today high is not the overnight low

- [x] **6.1 Do not copy night low into `hiF`**
  - After sunset the first NWS 12h period is “Tonight” (`isDaytime=false`). `hiF = a.hi ?: a.lo` makes `H:61° L:61°` while the actual day high already happened.
  - `DailyPoint.hiF` is currently a non-null `Double`. Make `hiF` nullable **or** fill from hourly:
    1. Daytime period temp → high.
    2. Else max remaining hourly temp for that local date.
    3. Else `hiF = null` and UI omits `H:`.
  - Never use the night low as the high.
  - Hero, daily pills, compact widget, wide widget all go through one formatter (`Fmt.hilo` or similar) so they cannot drift.
  - Files: `NwsSource.kt:153-162`, `Models.kt:58-65`, `WeatherScreen.kt:314-320`, `DailyCard.kt`, `WeatherWidgets.kt:82-85`.

---

## Phase 4 — ZIP change and widget chrome

### 7. Failed ZIP change must not keep the old city

- [x] **7.1 Clear on-screen data as soon as `setZip` succeeds**
  - `setZip` writes Prefs and `clearCaches()` before `refresh(force = true)`. If refresh fails, `Failure.stale` is null, `applyResult` only sets `errorMsg`, and `data` stays on the **previous** place. Header uses `data?.place ?: Prefs.place`, so the old city and forecast remain while Prefs already has the new ZIP. Widgets only update on Success, so they keep the old place too.
  - On ZIP change: `data = null` (or a place-only shell), `WidgetUpdater.updateAll(context, null)` (or a “loading” RemoteViews), then refresh. On failure, show the error on the empty/welcome state — not the previous city’s hero.
  - If `setZip` throws (bad ZIP / network), leave `data` and Prefs alone (current catch path).
  - Files: `WeatherScreen.kt:216-234`, `WeatherScreen.kt:73-85`, `WeatherScreen.kt:110`, `WeatherRepository.kt:207-214`.

### 8. Wide widget is 4×2, not 4×1

- [x] **8.1 Provider size matches the layout**
  - Layout is location + 48sp temp + 6-hour strip (~150 dp). Provider is `minHeight="57dp"` / `targetCellHeight="1"`. Launchers place a one-cell-tall widget and clip the hourly row. Compact 2×2 is already consistent (`110dp` / height 2).
  - Set `minHeight` ~110dp (or 100–125, match compact), `targetCellHeight="2"`. Add `minResizeHeight` so resize cannot collapse under the strip.
  - README already says 4×2; keep it in sync.
  - Files: `app/src/main/res/xml/widget_wide_info.xml`, `README.md` widgets bullet.

Manual check: add the Forecast widget on GrapheneOS/Pixel launcher — default size is two cells tall, hourly strip fully visible.

---

## Phase 5 — network budget (widgets actually complete)

### 9. NWS fetch cannot spend 60s

- [x] **9.1 Parallelize after `/points`**
  - `Http.get` timeout is 12s × (points + hourly + daily + stations + obs) = up to 60s. Widget budget is 8.5s, so a cold widget with a saved ZIP usually shows “Tap to set ZIP”.
  - After `/points`, fetch hourly and daily in parallel. Stations/obs stay best-effort and must not block the forecast pair. Consider a shorter timeout (4–6s) on obs only.
  - `fetchBodies` becomes `suspend` (or uses executors). `refreshLocked` is already a coroutine.
  - Files: `Http.kt`, `NwsSource.kt:37-68`, `WeatherWidgets.kt:166`.

- [x] **9.2 Widget retry**
  - Phase 1 already retries on the next 30-minute tick when stale. After this change, a first-add with a saved ZIP should usually succeed inside 8.5s. If not, next tick retries (do not require opening the app).

---

## Phase 6 — condition mapping

### 10. Dual NWS icons pick the severe condition

- [x] **10.1 Scan every icon token, not the first `[a-z_]+`**
  - Live 12h URLs look like `…/day/sct/tsra_hi,40` (“Mostly Sunny then Chance Showers And Thunderstorms”). Current regex takes `sct` → `PARTLY_CLOUDY` and drops the thunderstorm.
  - Split on `/` and `,` after `day|night`, map each token, return the most severe (tstorm > snow/sleet/rain > fog > cloud > clear).
  - Add missing tokens: `rain_showers_hi`, `wind_few`, `wind_sct`, `wind_bkn`, `wind_ovc`, `wind_skc` (already mapped).
  - Text fallback remains last resort.
  - Files: `Models.kt:116-140`, `LogicTests.kt` (`nws icon urls map correctly`).

---

## Phase 7 — polish (do with the rest, cheap)

### 11. Units toggle vs details grid

- [x] **11.1 Pick one product behavior and match the copy**
  - Today the dialog says “TEMPERATURE UNITS” and only `Fmt.temp` converts. Wind stays mph, visibility miles, pressure inHg.
  - Either:
    - **A (recommended for a US ZIP app):** keep imperial details, rename the dialog to “Temperature” so it is not a lie, **or**
    - **B:** convert wind (mph↔km/h), visibility (mi↔km), pressure (inHg↔hPa) with the same `Units` flag.
  - Widgets that show only temp are fine either way.
  - Files: `SettingsDialog.kt:89-94`, `DetailCards.kt:48-65`, `Fmt.kt`.

### 12. Drop unused permission

- [x] **12.1 Remove `ACCESS_NETWORK_STATE`**
  - Declared and mentioned in README; no Kotlin reads `ConnectivityManager`. Extra permission on GrapheneOS for no behavior.
  - Only keep it if Phase 1 adds a real offline/network banner that queries it.
  - Files: `AndroidManifest.xml:6`, `README.md` permissions sentence.

### 13. Rain color alpha

- [x] **13.1 Full ARGB literal**
  - Lint `MissingColorAlphaChannel`: `Color(0xCFE3F5)`. RGB is the intended rain tint; constructor is wrong.
  - Use `Color(0xFFCFE3F5).copy(alpha = …)`.
  - Files: `WeatherBackground.kt:229`.

### 14. Comments that restate the code

- [x] **14.1 Keep WHY, drop cleanroom/marketing**
  - Keep: NWS User-Agent, `Locale.US` in the points URL, goAsync 8.5s bound, mutex-is-process-wide (after 3.1).
  - Drop/shorten: widget KDoc “every mature FOSS weather app”, `WeatherBackground` “Cleanroom re-imagining…”, `Models.kt` “Cleanroom implementations…”.
  - Files: `WeatherWidgets.kt:23-30`, `WeatherBackground.kt:22-26`, `Models.kt:91-94`, `WeatherRepository.kt` header.

---

## Phase 8 — tests that lock the above in

Phase 0 added the failing cases. This phase is “green and complete”.

- [x] **8.1 Parser fixtures live in `app/src/test/resources/`**
  - Trimmed real NWS obs + 12h forecast (evening Tonight period) + Open-Meteo `current`/`current_units` (`m` and `ft`).
  - Do not hit the network in unit tests.

- [x] **8.2 Repository freshness**
  - If `WeatherRepository` is hard to unit-test (needs `Context`), extract `fun isFresh(fetchedAt, now, staleMs)` and stamp read/write to a `File` backend with a temp dir.
  - Assert: missing stamp → stale; stamp within 15 min → fresh; 16 min → stale.

- [x] **8.3 Condition + Fmt regressions stay**
  - Existing SunCalc / WMO / wind-cardinal / UV tests still pass.

- [x] **8.4 Run**
  ```bash
  ./gradlew :app:testDebugUnitTest :app:lintDebug
  ```

---

## Phase 9 — release hygiene (personal vs public)

v1.0 for **your** GrapheneOS install can keep the in-tree keystore. Do not publish that key.

- [x] **9.1 Personal sideload (keep)**
  - Local `keystore/xxweather.jks` + `keystore.properties` (gitignored) so rebuilds update in place.
  - Signing passwords are not in Gradle or git.

- [x] **9.2 Public repo**
  - Load `storeFile` / passwords from `keystore.properties` (gitignored).
  - `.jks` stays gitignored. Public clones get an unsigned release unless they supply their own key.

- [x] **9.3 Version bump after the bug fixes**
  - `versionCode = 2`, `versionName = "1.0.1"` in `app/build.gradle.kts`.
  - Rebuild: `./gradlew :app:assembleRelease`.
  - `apksigner verify` on `app/build/outputs/apk/release/app-release.apk`.

---

## Manual QA (device)

Do this on the GrapheneOS phone after Phase 1–4, before calling it done.

- [ ] First run: empty state → Set ZIP → hero, hourly, 10-day, details all populate.
- [ ] Kill app, reopen inside 15 min: cache paints instantly; no request (or mark `fromCache=true` in log).
- [ ] Reopen after 16 min: network runs; “Updated …” moves.
- [ ] Pull airplane mode, tap Refresh: stale forecast stays, error banner, stamp does **not** jump to now.
- [ ] Change ZIP to a real code: city and forecast switch. Change to `00000` or airplane mode mid-save: no leftover previous city.
- [ ] After sunset: Today high ≠ overnight low (or H is omitted), not `H:61 L:61`.
- [ ] Precip-chance tile is a number when hourly PoP exists, not `--`.
- [ ] Visibility on a clear day is ~10 mi or more, not ~3 mi from the extras path.
- [ ] Pressure at elevation (Denver / mountain ZIP) is ~30 inHg, not ~25.
- [ ] Add Compact 2×2 and Forecast 4×2. Forecast is two cells tall; hourly strip is not clipped.
- [ ] Leave both widgets overnight; morning values differ from last night (not just the clock).
- [ ] °F / °C toggle: hero and hourly convert; details match whatever Phase 7 chose.
- [ ] No location / sensors / network-state prompt on GrapheneOS.

---

## Suggested order in a single session

1. Phase 0 tests (red).
2. Phase 1 freshness + Phase 2 lock (unblocks widgets and the app).
3. Phase 3 units / precip / H-L (red tests go green).
4. Phase 4 ZIP + 4×2 widget.
5. Phase 5 parallel NWS fetch if widgets still time out in QA.
6. Phase 6 dual icons (tests already in 0.4).
7. Phase 7 polish.
8. Phase 8 + device QA.
9. Version bump, release APK, Phase 9 note.

Skip Phase 9.2 unless the APK is leaving this machine.
