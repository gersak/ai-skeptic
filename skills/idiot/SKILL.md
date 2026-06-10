---
name: idiot
description: Record that Claude's latest response or action was idiotic, with the user's reason, as an event in ~/.ai-skeptic/idiots.edn. Use when the user runs /idiot, or says a response was dumb/wrong/idiotic and wants it counted. Read back per-day via /bubble_cost.
---

# /idiot — log a bad response (Babashka)

The user is telling you your last response or action was idiotic. Log it:

```bash
bb ~/.ai-skeptic/bin/idiot.clj "<the user's reason>"
```

- The reason is everything the user typed after `/idiot`. If they gave none,
  ask for a one-line reason (what made it idiotic), then log that.
- After logging, show the script's confirmation line and respond briefly and
  **without being defensive** — acknowledge it, and in one sentence say what
  you'll do differently. Do not argue or over-apologize.
- Events are appended to `~/.ai-skeptic/idiots.edn` (one map per event, with
  date/time/reason/session/cwd). `/bubble_cost` re-derives the per-day 🤦 counts from it.
