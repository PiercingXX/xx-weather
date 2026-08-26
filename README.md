# XX Weather

A cleanroom, Pixel-Weather-inspired Android weather app built for **GrapheneOS**.
Single ZIP code setup, home-screen widgets, animated condition backgrounds, and
the most accurate free weather pipeline available for US locations — with **no
Google Play Services, no location permission, no tracking**.

## Accuracy pipeline (researched)

| Priority | Source | Role |
|---|---|---|
| 1 | **NWS `api.weather.gov`** (NOAA) | Primary forecasts. Human-edited NDFD point forecasts — the most accurate short-range source for US locations. Includes nearest-station METAR observations for current conditions. |
| 2 | **Open-Meteo** (`best_match` = NOAA HRRR/NBM/GFS blend over CONUS) | Enrichment: UV index, visibility, pressure, dew point, sunrise/sunset. |
| 3 | **Open-Meteo standalone** | Automatic full fallback if NWS is unreachable. |

- ZIP → lat/lon: [api.zippopotam.us](https://www.zippopotam.us/) (keyless), cached after first use.
- Sunrise/sunset also computed locally via the NOAA solar equations (works offline).
- All endpoints are HTTPS, keyless, and free; Open-Meteo data is CC-BY 4.0 (attributed in-app).

## Features

- Hero current conditions (temp, condition, H/L, feels-like) over an animated
  gradient sky: sun rays, drifting clouds, rain, snow, lightning, fog, stars.
- Hourly carousel (24 h) and a 10-day pill strip with week-scaled range bars.
- Detail tiles: feels-like, humidity/dew point, wind (+compass arrow), UV,
  pressure, visibility, sunrise/sunset, precip chance.
- °F / °C toggle.
- Multiple saved ZIP codes; swipe left/right between full forecasts, or
  toggle the collapsed list and tap a city for detail.
- Two widgets: **Compact (2×2)** and **Forecast (4×2 with 6-hour strip)**.
  Updates every 30 min (system floor), immediately after in-app refreshes, and
  render instantly from cache.
- Offline-first: raw API responses are cached to app storage; stale data stays
  visible with an "Updated" stamp if the network fails.

## Cleanroom statement

No proprietary code was viewed or copied. The UI/UX was specified from public
reviews and screenshots of the Pixel Weather app's *observable behavior*; all
code, icons, and assets were written from scratch. Architectural patterns
(repository, pluggable sources, RemoteViews widgets) follow publicly documented
conventions of FOSS weather apps (Breezy Weather, QuickWeather, et al.).

## Build

Requirements: JDK 17+, Android SDK (platform 36, build-tools 35).

```bash
./gradlew :app:assembleDebug
```

A signed release APK needs a local `keystore.properties` (see
`keystore.properties.example`) pointing at your own `.jks`. Do not commit
the keystore, that properties file, or an APK signed with a personal key.

```bash
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

## Install on GrapheneOS

**Option A — ADB**
```bash
adb install app-release.apk
```

**Option B — Browser (no PC)**
1. Copy the APK to the phone (USB, Syncthing, etc.).
2. Settings → Apps → Special app access → Install unknown apps → allow your
   browser or Files app.
3. Open the APK → Install.

The app requests only `INTERNET`. No GMS dependency,
so it runs identically on GrapheneOS, CalyxOS, or stock Android 8+.

## First run

Open the app → **Set ZIP Code** → enter any 5-digit US ZIP → Save. That's it.
Long-press the home screen → Widgets → XX Weather to add either widget.
