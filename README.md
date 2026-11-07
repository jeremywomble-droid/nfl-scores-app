# NFL Scores (Android)

Live NFL scoreboard app + semi-transparent home-screen widget.

- App: every game for the current week (live / final / upcoming), auto-refreshing,
  plus a locked "Claude's Predictions" section for the synced week: the actual score on top,
  the locked prediction underneath (never changes as games progress), a per-game
  confidence %, and a running stat line (winner picks correct, exact scores).
- Widget: semi-transparent (62% opaque black, rounded) home-screen widget with
  a SCROLLABLE list of all games in the synced predictions week, actual scores, and predictions
  underneath. Refresh is game-aware (system widget timer OFF): ~5 minutes
  while games are live, a wake-up at each kickoff, ~12h when all games are
  final; opening the app also refreshes the widget. Tap to open the app.
- Data: ESPN's public scoreboard API, fetched directly by the app.
- Predictions come from the **nfl-scores web app** (the analyst model whose
  picks lock weekly in its game_picks store) — one source of truth for both
  surfaces. They are synced into app/src/main/assets/predictions.json at
  build time: invoke the web app's `getscores` action for the week, save the
  JSON, run `python3 scripts/sync_predictions.py <file>`, commit, push.
  The web artifact is not publicly reachable over HTTP, so the app cannot
  fetch picks at runtime; the bundled file states its week and sync time,
  and the app/widget read the week from the file (never hardcoded).
