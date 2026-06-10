# my section
This was FAFO project that i'll try to reason about after using it for a while.

Lately I've found that Anthropic is quite generous. I've been using Claude Max subscription
to work in parallel on multiple projects. What I noticed was that my day-to-day spending
is very profitable. For me... So I've decided to make AI code statistics for what my day to
day usage looks like.

Two tools:
 * **`/bubble_cost`** - check out how much have you been sponsored by AI investors
 * **`/idiot`**       - probably not politically correct, but if it helps you you can read it like, "Oh, my dear sweet idiot... What have you done this time?" counter with note what happened

So how much are they treating you with? Find out ;)


BTW. I'm not skeptic (at least for result side, even though mixed feelings)
and I really dislike the hype (business model?, application?).

If greatest achivement of AI is building applications and coding 
(that btw get worse every year, starting from era when developers 
DIDN'T HAVE TO OPTIMISE for resources, CPU/RAM/Networking) than we are all F***D.


I would like to see it applied to help find cure for cancer research, CO2 emmision mitigation, 
resource planning, like how can we spend watter more efficiently, energy source researching,
new materials... etc...


One more thing... I do use AI. I find it usefull for refactoring, I find it great for log analysis.
I like to use it when setting up development infrastrucutre/docker containers and even for generating
tests. I don't like it in situations where I need to call `/idiot` skill, and that is mostly during
coding most valuable part!


And last... Excuse me... for spelling. I'm not native English speaker


# P.S. IMPORTANT
If this doesn't work for you, its not me its AI. I didn't write the code

@claude - don't change my section ever


# ai-skeptic

A tiny, read-only **cost + quality scorecard** for [Claude Code](https://claude.com/claude-code).

It reads the transcript files Claude Code already writes to `~/.claude/projects/`,
estimates what each session and day cost (from exact token counts at list prices),
and lets you log the times Claude was an idiot — so you get a Usage-tab-style
report *plus* a running 🤦 count, persisted as plain EDN you can graph.

```
      Total cost:        $1.17
    Duration (API):    4m 5s
    Duration (wall):   51m 19s
    Code changes:      0 lines added, 0 removed
    Usage by model:
      claude-opus-4-8:     5.7k input · 11.7k output · 917.4k cache read · 39.4k cache write   ($1.17)

    PER DAY  (all projects)

    day               cost   🤦
    ------------ ---------   ---
    2026-06-10      $26.95     0
    2026-06-09     $110.56     0
    2026-06-08     $294.26     0
    2026-06-06       $5.17     0
    2026-06-05     $266.74     0
    2026-06-04      $61.33     0
    2026-06-03     $214.28     0
    2026-06-02     $395.96     0
    2026-06-01     $308.97     0
    2026-05-31      $50.43     0
    2026-05-30      $44.55     0
    2026-05-29     $374.40     0
    2026-05-28     $317.74     0
    2026-05-27     $786.77     0
    2026-05-26     $107.79     0
    2026-05-25     $486.33     0
    2026-05-24     $221.52     0
    2026-05-23     $213.48     0
    2026-05-22     $272.27     0
    2026-05-21     $193.77     0
    2026-05-20     $216.66     0
    2026-05-19     $290.11     0
    2026-05-18      $31.16     0
    2026-05-17     $482.89     0
    2026-05-16      $74.06     0
    2026-05-14      $18.77     0
    2026-05-13     $220.29     0
    2026-05-12     $129.59     0
    2026-05-11      $53.39     0
    ------------ ---------   ---
    TOTAL         $6270.19     0


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

Three files are maintained under `~/.ai-skeptic/`, each a vector of maps — one
row per observation, ready to drop into a plot:

| file           | one row per | written by         | committed? |
| -------------- | ----------- | ------------------ | ---------- |
| `sessions.edn` | session     | `scan.clj` (cumulative ledger) | no (gitignored) |
| `days.edn`     | day         | `scan.clj` (derived from ledger) | no (gitignored) |
| `idiots.edn`   | 🤦 event    | `idiot.clj` (append-only) | no (gitignored) |

These hold *your* personal usage and are gitignored on purpose.

### Cumulative ledger (why totals don't reset)

Claude Code only keeps about **30 days** of transcripts on disk and prunes older
ones. A naive scan would therefore silently turn into a rolling 30-day window —
the all-time TOTAL would quietly stop growing and old days would drop off the
bottom as their transcripts aged out.

To avoid that, `scan.clj` keeps `sessions.edn` as a **persistent ledger**. Each
run merges the fresh scan into it: freshly-scanned sessions are authoritative for
the ids they cover, and previously-recorded sessions survive for ids whose
transcripts have already been pruned. `days.edn` and the printed totals are then a
pure projection of that ledger, so a day keeps counting even after its transcripts
are gone (and a *partially* pruned day keeps the sessions it already recorded).

Two consequences worth knowing:

- **History accrues from your first run forward.** The ledger can only remember
  days it has seen at least once — it cannot recover sessions that were pruned
  before you ever ran the scan. Run `/bubble_cost` regularly to keep it complete.
- **`sessions.edn` is the source of truth.** Back it up if you want a permanent
  record; deleting it resets history to whatever is currently on disk.

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
│   ├── scan.clj      # merge scan into the ledger + print the report
│   └── idiot.clj     # append one 🤦 event to idiots.edn
├── skills/           # vendored copies of the Claude Code slash-commands
│   ├── bubble_cost/SKILL.md
│   └── idiot/SKILL.md
├── README.md
├── .gitignore
├── sessions.edn      # cumulative ledger, gitignored (source of truth)
├── days.edn          # derived from the ledger, gitignored
└── idiots.edn        # append-only, gitignored
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

**Will the TOTAL keep growing forever?** Yes — that's the point of the cumulative
ledger (see above). It accrues from your first run onward; it can't back-fill days
whose transcripts were already pruned before you started. Run it regularly and
keep `sessions.edn` if you want an unbroken record.
