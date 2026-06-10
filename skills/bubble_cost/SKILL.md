---
name: bubble_cost
description: Rebuild and show per-day Anthropic cost + 🤦 idiot counts and the current session's usage breakdown (Usage-tab style), across all Claude Code projects. Data is persisted as graphable EDN under ~/.ai-skeptic/. Use when the user asks what a session/day cost, "what's my tab", or runs /bubble_cost. Pairs with /idiot.
---

# /bubble_cost — cost + idiot scorecard (Babashka)

Run the scan; it rebuilds the EDN snapshots and prints the summary:

```bash
bb ~/.ai-skeptic/bin/scan.clj
```

Show the printed output verbatim. Two parts, mirroring Claude Code's Usage tab:
1. **SESSION** — current session's total cost, API + wall duration, code
   changes, and a per-model token + cost table.
2. **PER DAY** — cost + 🤦 idiot count per day (all projects), with totals.

Every run rewrites the graphable data (vectors of maps, one row per
observation — ready for a histogram/plot):
- `~/.ai-skeptic/sessions.edn` — one map per session (usage tracking)
- `~/.ai-skeptic/days.edn` — one map per day (the report)
- `~/.ai-skeptic/idiots.edn` — one map per idiot event (authoritative; written by /idiot)

Notes:
- `scan.clj` is read-only against transcripts and idempotent — safe to run anytime.
- Cost is **estimated** from token counts at list prices (Opus $5/$25, Sonnet
  $3/$15, Haiku $1/$5 per 1M in/out; cache read 0.1×, 5-min write 1.25×, 1-hr 2×).
  Token counts are exact; the dollar figure is the estimate.
- Only sees sessions on **this machine**. "this session" = newest transcript in cwd.
