# XX-Weather — Remaining work

**2026-09-04.** Forecast pipeline + widgets + WorkManager 15-min refresh
are code (`versionName` 1.2.0). Remaining work is **alerts + device QA**.
Old “code phases” stay in git; do not re-litigate freshness/units.

Package: `com.xx.weather`  
US ZIP weather. NWS primary, Open-Meteo fallback. No location. No GMS.
`INTERNET` required.

```
Status: app + widgets exist. POST_NOTIFICATIONS is declared and unused.
Manual QA unchecked. Docs still talk like 1.0.1 / no WorkManager.
```

---

## Locked now (2026-09-04)

| ID | Decision |
|---|---|
| W1 | **Implement weather alerts.** That is why `POST_NOTIFICATIONS` is there. |
| W2 | Device QA **is** the rest of the ship list. |
| W3 | Units stay temperature-only (Phase 7 option A). |

---

## W1 — Alerts

- [ ] Runtime `POST_NOTIFICATIONS` on first alert enable (API 33+). Deny
  → alerts stay off, forecast still works. Honest copy.
- [ ] Per-ZIP (or current ZIP) user-defined triggers. v1 minimum:
  **precip in the next N hours** and **temperature cross** (at/below a
  threshold). No spam: one notification per trigger per fetch window.
- [ ] WorkManager already refreshes every 15 min — evaluate alerts off
  that pass, not a second poller.
- [ ] Tap opens the app on that ZIP. GrapheneOS Network revoke → no
  crash, no fake “updated just now.”
- [ ] README stops saying INTERNET-only; list notifications as optional.
- **Accept:** enable precip alert, wait for a real/forced fetch that
  meets the rule, see one notification. Disable → silence.

---

## Manual QA (device)

- [ ] First run: empty → Set ZIP → hero, hourly, 10-day, details populate
- [ ] Kill, reopen inside 15 min: cache paints; no needless request
- [ ] Reopen after 16 min: network; “Updated …” moves
- [ ] Airplane + Refresh: stale forecast stays, error banner, stamp does not jump to now
- [ ] Real ZIP switch; `00000` / airplane mid-save does not leave the old city
- [ ] After sunset: Today high ≠ overnight low (or H omitted)
- [ ] Precip-chance tile is a number when hourly PoP exists
- [ ] Visibility on a clear day is ~10 mi+, not ~3 from the extras path
- [ ] Pressure at elevation is ~30 inHg, not ~25
- [ ] Compact 2×2 and Forecast 4×2; Forecast is two cells tall; hourly not clipped
- [ ] Leave widgets overnight; morning values differ
- [ ] °F / °C: hero + hourly convert
- [ ] No location / sensors prompt on GrapheneOS
- [ ] Alert path from W1 on this phone

**Accept:** dated notes. Then personal sideload is 1.2.

---

## Docs

- [ ] TODO / WORKFLOW_STATE / README: version 1.2.0, 15-min WorkManager,
  alerts. WORKFLOW_STATE must not claim “WorkManager not added.”

---

## Stop conditions

- Location / GMS / tracking → reject.
- Declaring THEME_SYNC as a `<permission>` → reject.
- A second network poller just for alerts → reject.
- F-Droid/public keystore in git → reject.
