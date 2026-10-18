# NFL Scores (Android)

Live NFL scoreboard app + semi-transparent home-screen widget.

- App: every game for the current week (live / final / upcoming), auto-refreshing,
  plus a locked "Claude's Week 7 Predictions" section: the actual score on top,
  the locked prediction underneath (never changes as games progress), a per-game
  confidence %, and a running stat line (winner picks correct, exact scores).
- Widget: semi-transparent (62% opaque black, rounded) home-screen widget with
  the Week 7 games, actual scores, and predictions underneath; refreshes every
  30 minutes; tap to open the app.
- Data: ESPN's public scoreboard API, fetched directly by the app.
- Predictions were locked on 2026-10-18 from a stats-through-Week-5 model and
  live in app/src/main/assets/predictions.json.
