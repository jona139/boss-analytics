# Boss Analytics — RuneLite Plugin

A data collection plugin for OSRS that tracks boss kill times, player stats, gear loadouts, raid routes, and team compositions. Designed for statistical analysis of PvM efficiency.

## Architecture

```
BossAnalyticsPlugin (main entry point)
├── tracking/
│   ├── KillTracker          — Core kill detection (chat parsing + NPC death events)
│   ├── PlayerStateTracker   — Gear, levels, CA tier snapshots
│   └── BossRegistry         — Boss definitions (NPC IDs, chat patterns, 40+ bosses)
├── bosses/
│   ├── CoxHandler           — Chambers of Xeric (room tracking, route detection, points)
│   └── NexHandler           — Nex (team size, phase timing, kill classification)
├── data/
│   ├── DataStore            — SQLite persistence (abstracted for future backend)
│   ├── KillRecord           — Core data model per kill
│   └── RaidRecord           — Extended model for raids with room-level detail
├── export/
│   └── DataExporter         — CSV/JSON export (pandas-friendly flat format)
└── ui/
    └── BossAnalyticsPanel   — Side panel (kill counts, averages, export buttons)
```

## Data Collected Per Kill

| Field | Source | Notes |
|-------|--------|-------|
| Kill time | Chat message (primary) or tick counting (fallback) | Chat is authoritative |
| All skill levels | Client API | Both real and boosted |
| Full gear snapshot | Equipment container | All 14 equipment slots |
| Combat Achievement tier | Varbit 12862 | 0=none through 6=grandmaster |
| Kill count | Chat message | Parsed from KC message |
| Personal best | Chat message | Detected from "(new personal best)" |
| Slayer task status | Chat tracking | Whether on-task |
| Team size | Player count in instance | For group content |
| World | Client API | For world-type analysis |

### Raid-Specific (CoX, ToA, ToB)

| Field | Source | Notes |
|-------|--------|-------|
| Room order (route) | NPC spawn detection | Which rooms in which order |
| Room durations | Tick counting per room | Time per room |
| Total/personal points | Varbits | Raid points |
| Deaths per room | Player death events | Personal + team |
| Purple detection | Chat message | Unique drop flag |

### Nex-Specific

| Field | Source | Notes |
|-------|--------|-------|
| Phase durations | Overhead text transitions | Smoke→Shadow→Blood→Ice→Zaros |
| Team size | Player instance scan | Counted at fight start |
| Kill classification | Team size bucketing | small_team/mid_team/mass |
| Per-phase deaths | Death + phase tracking | Which phases are deadliest |

## Export Format

CSV exports are designed for direct `pd.read_csv()` consumption:

```python
import pandas as pd

kills = pd.read_csv('~/.runelite/boss-analytics/export/vorkath_kills.csv',
                     parse_dates=['timestamp'])

# Average kill time by weapon
kills.groupby('weapon_name')['duration_seconds'].mean().sort_values()

# Kill time trend over KC
kills.plot(x='kill_count', y='duration_seconds', kind='scatter')
```

## Development Roadmap

### Phase 1 — Core (this codebase) ✅
- [x] Kill detection via chat + NPC death
- [x] Player state snapshots
- [x] SQLite local storage
- [x] 40+ boss definitions
- [x] CSV/JSON export
- [x] Side panel UI

### Phase 2 — Raid Polish
- [ ] CoX room detection refinement (object-based puzzle detection)
- [ ] ToA invocation level tracking + room times
- [ ] ToB room handler (Maiden, Bloat, Nylo, Sotetseg, Xarpus, Verzik)
- [ ] Purple item name parsing from loot messages

### Phase 3 — Analysis Features
- [ ] In-plugin charts (kill time trends, gear comparison)
- [ ] Supply usage tracking (inventory diff pre/post kill)
- [ ] DPS estimation from hitsplat tracking
- [ ] GP/hr calculation with live GE prices

### Phase 4 — Backend (Optional)
- [ ] REST API backend for aggregated data
- [ ] Opt-in anonymous data upload
- [ ] Population-level statistics
- [ ] Web dashboard

## NPC ID Verification

⚠️ **The NPC IDs in `BossRegistry` are approximate.** Before deploying:

1. Verify all IDs against the [OSRS Wiki NPC database](https://oldschool.runescape.wiki/w/Category:Non-player_characters)
2. Cross-reference with RuneLite's `NpcID` constants
3. Test each boss in-game to confirm detection works

Some bosses have IDs that change with game updates. The registry is designed to
be easily updated — each boss is a single `register()` call.

## Building

```bash
./gradlew build
```

The output JAR goes in `build/libs/`. Load it as an external RuneLite plugin
or add to your plugin-hub fork.

## License

MIT — built by GamecubeJona for data-driven OSRS content.
