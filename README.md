# ai-skeptic

A tiny, read-only **cost + quality scorecard** for [Claude Code](https://claude.com/claude-code).

It reads the transcript files Claude Code already writes to `~/.claude/projects/`,
estimates what each session and day cost (from exact token counts at list prices),
and lets you log the times Claude was an idiot — so you get a Usage-tab-style
report *plus* a running 🤦 count, persisted as plain EDN you can graph.

```
  PER DAY  (all projects)

  day               cost   🤦
  ------------ ---------   ---
  2026-06-10      $25.78     0
  2026-06-09     $110.56     1
  ...
  ------------ ---------   ---
  TOTAL         $6308.27     3
```

Two commands:

- **`/bubble_cost`** — rebuild the snapshots and print the report (this session +
  every day across all projects).
- **`/idiot <reason>`** — log that Claude's last response was bad, with your reason.
  The day's 🤦 count is re-derived from this on the next `/bubble_cost`.

---

## How it works

Claude Code writes one JSONL transcript per session under
`~/.claude/projects/<encoded-cwd>/<session-id>.jsonl`. Each assistant turn records
exact token usage (input / output / cache-read / cache-write). The scanner walks
every transcript, dedupes by message id, prices each turn, and rolls the results
up per session and per day. **It never modifies the transcripts.**

Three files are (re)generated under `~/.ai-skeptic/`, each a vector of maps — one
row per observation, ready to drop into a plot:

| file           | one row per | written by         | committed? |
| -------------- | ----------- | ------------------ | ---------- |
| `sessions.edn` | session     | `scan.clj`         | no (gitignored) |
| `days.edn`     | day         | `scan.clj`         | no (gitignored) |
| `idiots.edn`   | 🤦 event    | `idiot.clj` (append-only) | no (gitignored) |

These hold *your* personal usage and are gitignored on purpose — clone the repo
and they regenerate from your own transcripts on first run.

### Pricing

Cost is **estimated** at list prices per 1M tokens — Opus $5/$25, Sonnet $3/$15,
Haiku $1/$5 (in/out) — with cache read at 0.1×, 5-minute cache write at 1.25×, and
1-hour cache write at 2× the input rate. Token counts are exact; the dollar figure
is an estimate and your actual billing (e.g. on a subscription plan) may differ.
Adjust the `price` map at the top of `bin/scan.clj` if rates change.

### Scope

The scanner only sees transcripts **on this machine**. "This session" in the
report is the newest transcript in your current working directory.

---

## Requirements

[Babashka](https://github.com/babashka/babashka) (`bb`) — a fast-starting Clojure
scripting runtime. The scripts use only built-in libraries (`babashka.fs`,
`cheshire`, `clojure.edn`), so no extra dependencies.

```bash
# macOS / Linux
brew install borkdude/brew/babashka
# or
bash < <(curl -s https://raw.githubusercontent.com/babashka/babashka/master/install)
```

Both scripts are read-only against your transcripts and idempotent — safe to run
anytime.

---

## Install

```bash
git clone <this-repo> ~/.ai-skeptic
```

Wire up the two slash-command skills so Claude Code can run them for you:

```bash
mkdir -p ~/.claude/skills
cp -r ~/.ai-skeptic/skills/bubble_cost ~/.claude/skills/
cp -r ~/.ai-skeptic/skills/idiot       ~/.claude/skills/
```

Now `/bubble_cost` and `/idiot` are available in Claude Code. (If you cloned to a
path other than `~/.ai-skeptic`, update the `bb ~/.ai-skeptic/bin/...` paths inside
the two `SKILL.md` files.)

---

## Usage

### From inside Claude Code (intended)

```
/bubble_cost            # show the full cost + 🤦 report
/idiot it re-read the whole file instead of grepping
```

`/idiot` takes everything after the command as the reason. If you give none,
Claude will ask for a one-line reason, then log it — and respond without being
defensive.

### Directly from a shell

```bash
bb ~/.ai-skeptic/bin/scan.clj                 # rebuild snapshots + print report
bb ~/.ai-skeptic/bin/idiot.clj "why it was bad"   # log a 🤦 for today
```

---

## Layout

```
.ai-skeptic/
├── bin/
│   ├── scan.clj      # rebuild sessions/days EDN + print the report
│   └── idiot.clj     # append one 🤦 event to idiots.edn
├── skills/           # vendored copies of the Claude Code slash-commands
│   ├── bubble_cost/SKILL.md
│   └── idiot/SKILL.md
├── README.md
├── .gitignore
├── sessions.edn      # generated, gitignored
├── days.edn          # generated, gitignored
└── idiots.edn        # generated, gitignored (append-only)
```

---

## FAQ

**Does this send my data anywhere?** No. Everything is local file reads and writes.

**Can I run it without Babashka?** Not as-is — both scripts are `bb` scripts. You
could compile a native binary with GraalVM or port the logic, but Babashka is a
single ~20 MB download and by far the easiest path.

**Why are the `.edn` files empty after cloning?** They're gitignored; run
`/bubble_cost` (or `bb bin/scan.clj`) once and they populate from your own
transcripts.
