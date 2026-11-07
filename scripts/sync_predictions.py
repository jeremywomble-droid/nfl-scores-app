#!/usr/bin/env python3
"""Sync app/src/main/assets/predictions.json from the nfl-scores web app.

The web app (Muse artifact, slug "nfl-scores") is the single source of truth
for predictions: its `getscores` action returns the current week's games with
the analyst model's LOCKED picks (game_picks store). This script converts one
week's getscores payload into the Android app's predictions.json format.

Usage:
    1. Invoke the artifact action `getscores` (optionally with {"week": N})
       and save the JSON result to a file, e.g. /tmp/getscores.json
    2. python3 scripts/sync_predictions.py /tmp/getscores.json
    3. Commit the updated assets/predictions.json and push — CI builds the APK.

The web artifact is NOT publicly reachable over HTTP (fullstack artifact,
not shared), so the app cannot fetch picks at runtime; this sync is the
build-time bridge. The bundled JSON is a snapshot of the web app's locked
picks for that week, clearly superseded as soon as a newer sync lands.
"""
import json
import math
import sys
from datetime import datetime, timezone
from pathlib import Path

src = Path(sys.argv[1])
data = json.loads(src.read_text())
week = data["week"]
games = data["games"]

preds = []
for g in games:
    p = g.get("prediction")
    if not p:
        raise SystemExit(f"game {g['away']['abbr']} @ {g['home']['abbr']} has no prediction — refusing to sync a partial week")
    pick = p["pickAbbr"]
    pct = p["awayWinPct"] if p["pick"] == "away" else p["homeWinPct"]
    preds.append({
        "week": week,
        "away": g["away"]["abbr"],
        "home": g["home"]["abbr"],
        "predAway": p["projectedAway"],
        "predHome": p["projectedHome"],
        "pick": pick,
        "confidence": int(math.floor(pct + 0.5)),
    })

out = {
    "week": week,
    "seasonYear": data.get("seasonYear"),
    "source": "nfl-scores web app (getscores action) — analyst model locked picks; synced build-time by scripts/sync_predictions.py",
    "syncedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "predictions": preds,
}
dest = Path(__file__).resolve().parent.parent / "app/src/main/assets/predictions.json"
dest.write_text(json.dumps(out, indent=1) + "\n")
print(f"wrote {dest} — week {week}, {len(preds)} predictions")
