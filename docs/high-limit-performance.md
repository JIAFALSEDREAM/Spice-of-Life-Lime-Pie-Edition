# High-limit configuration performance

Measured on Linux, AMD Ryzen 7 5800H, GraalVM Community Java 21.0.2,
Minecraft 1.21.1 / NeoForge 21.1.251. These are local operation timings, not a multiplayer TPS benchmark.

Reproduce with:

```sh
JAVA_TOOL_OPTIONS=-Dsollimepie.benchmark=true ./gradlew build runGameTestServer --console=plain
```

`HighLimitGameTests.highScaleBenchmark` runs in the actual game test JVM. Each operation has
100 warm-up calls, then five batches of 200 calls; the table reports the median batch average.
The 27-slot inventory contains 27 distinct real foods. To model larger food sets without installing
a modpack, registered items (including non-food items) stand in for history entries with explicit
unit weights. Thus the history benchmark covers traversal and allocation costs, not arbitrary mod
food callbacks. `save-nbt` builds the NBT object; it does not measure disk writes or network transport.
The book benchmark creates reward information, not rendered GUI pages.

## Recorded timings

All times below are microseconds per operation from the 2026-10-01 run.

| History mode | Records | Score | Select among 27 foods | Build save NBT | Record meal |
|---|---:|---:|---:|---:|---:|
| Finite, decay enabled | 32 | 1.16 | 30.46 | 7.47 | — |
| Finite, decay enabled | 256 | 6.23 | 201.18 | 30.48 | — |
| Finite, decay enabled | 1000 | 25.32 | 817.32 | 152.89 | — |
| Permanent, decay disabled | 32 | 2.80 | 4.63 | 5.62 | 2.74 |
| Permanent, decay disabled | 256 | 7.35 | 4.72 | 31.60 | 5.72 |
| Permanent, decay disabled | 1000 | 20.58 | 3.20 | 150.10 | 21.28 |

Finite history uses `queueSize = endDecay = 1000`, `startDecay = 0`. Permanent history uses
`queueSize = 0`, `decayEnabled = false`. Scores therefore differ between these modes; this is a
configuration comparison, not a claim that disabling decay preserves the same scoring rules.
The permanent meal benchmark repeatedly refreshes an existing entry, keeping record count stable.

| Reward tiers (one luck modifier each) | Update before indexing | Update after indexing | Build book data |
|---:|---:|---:|---:|
| 17 | 5.45 | 7.68 | 2.21 |
| 256 | 188.51 | 50.88 | 13.40 |
| 1024 | 2958.06 | 106.63 | 18.74 |

The before/after columns come from separate runs on the same machine. Vanilla `AttributeInstance`
uses FastUtil's `Object2ObjectArrayMap` for modifier IDs, so one lookup is linear in modifier count.
Updating N existing rewards on one attribute previously performed N such lookups. Each reward
update now indexes the current modifiers once per touched attribute and skips unchanged applications
and absent removals. The index lasts only for that update, so external modifier edits remain visible.
First-time activation and actual removal still incur vanilla mutation costs.

## Interpretation

History stores one record per distinct item, rather than allocating `queueSize` slots. Raising the
configured limit alone does not allocate more memory. Score calculation, meal processing and save
construction scale with actual retained records. With decay enabled, food selection scans history
for each distinct candidate; permanent history without decay uses direct membership lookup.
Reward count remains unrestricted, but many active modifiers on one attribute can be costly.

Defaults remain 32 recent meals with decay enabled. Large modpacks should profile actual player
counts, potion refreshes, config/history payloads, GUI construction and GC pauses before choosing
large settings. The medians above exclude tail latency and provide no guarantee for 10000 records
or thousands of simultaneous players.
