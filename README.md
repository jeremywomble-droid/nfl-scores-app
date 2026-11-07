# NFL Scores (Android)

Live NFL scoreboard app + semi-transparent home-screen widget.

- App: every game for the current week (live / final / upcoming), auto-refreshing,
  plus a locked "Claude's Predictions" section for the current picks week: the actual score on top,
  the locked prediction underneath (never changes as games progress), a per-game
  confidence %, and a running stat line (winner picks correct, exact scores).
- Widget: semi-transparent (62% opaque black, rounded) home-screen widget with
  a SCROLLABLE list of all games in the current predictions week, actual scores, and predictions
  underneath. Refresh is game-aware (system widget timer OFF): ~5 minutes
  while games are live, a wake-up at each kickoff, ~12h when all games are
  final; opening the app also refreshes the widget. Tap to open the app.
- Data: ESPN's public scoreboard API, fetched directly by the app.
- Predictions come from the **nfl-scores web app** (the analyst model whose
  picks lock weekly in its game_picks store) — one source of truth for both
  surfaces. Since v1.2.2 the app fetches them AT RUNTIME from the feed file
  `data/predictions.json` in this repo (served via raw.githubusercontent.com):
  network-first on every app open and widget update, the last-good fetch is
  cached on-device, and the bundled app/src/main/assets/predictions.json is
  only the offline / first-run fallback snapshot (it is refreshed whenever a
  release is built anyway). The app/widget read the week from whichever file
  supplied the picks, never hardcoded.
- Updating the picks (weekly, NO build needed): invoke the web app's
  `getscores` action for the week, save the JSON, run
  `python3 scripts/sync_predictions.py <file> data/predictions.json`,
  commit and push just that file. Every installed app picks the new week up
  on its next open — no new APK, no reinstall.
