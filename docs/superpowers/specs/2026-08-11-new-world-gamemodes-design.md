# New World Gamemodes for Paper — Design

**Date:** 2026-08-11  
**Status:** Approved by the user’s instruction to accept recommended choices

## Goal

Build one standalone Paper plugin containing two isolated, configurable arena modes:

1. **Outpost Rush (OPR):** the New World 20v20, three-outpost, resource-building, PvPvE mode.
2. **Castle Siege (War):** the New World 50v50 attacker/defender territory-fort assault.

The plugin targets **Paper API `26.2.build.112-stable`**, **JDK 25**, and **Gradle Kotlin DSL**. It requires no client mod, resource pack, economy, permissions, world-management, or placeholder plugin.

## Approaches considered

1. **Two independent mode implementations.** Fast initially, but duplicates lifecycle, player restoration, arena cleanup, and persistence.
2. **Shared deterministic match engine with separate rules engines. Recommended.** Centralizes safety and lifecycle while keeping OPR and War mechanics independent.
3. **Generic scripted rules runtime.** Flexible, but harder to validate and debug, and unnecessary for two known modes.

The implementation uses approach 2.

## Architecture

- `GamemodesPlugin`: boot, strict configuration loading, listener/command registration, shutdown barrier.
- `MatchCoordinator`: global player ownership, arena reservations, match lifecycle, one serialized event stream per match.
- `OprMatch` and `SiegeMatch`: mode-specific state machines without direct Bukkit dependencies where practical.
- `ArenaCatalog`: validated arena definitions and immutable template paths.
- `ArenaInstanceManager`: clones templates only while unloaded, tags each clone with a generation ID, unloads and deletes instances idempotently.
- `PlayerStateService`: durable snapshot and pending-login restoration for inventory, cursor, armor, offhand, effects, attributes, health, food, XP, game mode, flight, and return location.
- `PersistenceStore`: SQLite match snapshots with sequence/CAS ordering, wall-clock deadlines for restart recovery, and monotonic clocks while running.
- `RewardOutbox`: transactional, idempotent internal reward grants. External economy integrations are out of scope.
- `PaperGateway`: teleport, entity, block, projectile, inventory, scoreboard, boss-bar, action-bar, and world operations.

Shutdown rejects new ingress, stops match timers, serializes final checkpoints, restores online players, records pending restores for offline players, unloads arena worlds, then closes SQLite.

## Common arena flow

`DISABLED → WAITING → PREPARING → ACTIVE → RESOLVING → CLEANUP → WAITING`

- A player UUID can belong to only one queue or match.
- Teams differ by at most one player.
- Recommended quorum is 50% of nominal capacity; admins may configure lower values explicitly.
- No-shows are removed at preparation expiry. The match starts only if both teams still satisfy quorum.
- Disconnects reserve the slot for five minutes. Reconnect restores the same team and match inventory. Expired disconnects apply the normal death/resource-loss rule.
- Abort restores players and arena state but grants no victory reward.
- All terminal events are ordered: objective/kill/boss event for a tick, score update, victory check, then deadline check.
- Outsiders cannot enter through commands, teleports, portals, vehicles, pearls, chorus fruit, beds, or respawn routing.
- Arena blocks are immutable except registered gates, structures, resource nodes, and plugin-authorized siege effects.

## Outpost Rush

### Source-faithful rules

- Two teams, nominally 20 players each; parties up to five.
- Team forts at opposite ends; three outposts equivalent to Luna, Sol, and Astra.
- Outposts are captured by occupying their zones. Enemy presence contests and pauses progress.
- Each owned outpost awards one team point every three seconds.
- An eligible enemy player kill awards one team point.
- First team to 1,000 points wins; otherwise the higher score at expiry wins.
- Resources: infused wood, ore, hides, and Azoth Essence.
- Carried resources lose 50% on death; resources deposited in team storage are safe.
- Outposts support an Armory, shared Storage Shed, two gate upgrades, two Command Post upgrades, a Protection Ward, repairs, rampart siege, and team respawn.
- A Ward blocks capture while active. Command Posts provide configurable damage/armor bonuses and battle-token generation.
- Baroness Hain spawns every ten minutes. The credited team receives regeneration and defense. The opponent’s score is frozen: 60 seconds when the leading team wins, scaling to 150 seconds when the trailing team wins based on deficit.
- The Corrupted Portal recurs every five minutes. Clearing the wave and closing the portal awards a Brute summon.
- Bear, Specter, and Brute summons deploy at owned summoning circles and are leashed to configured objectives.
- Respawns occur in synchronized waves.

### Minecraft adaptations and defaults

- Match duration: 30 minutes. New World’s current public official pages do not publish a definitive duration.
- Capture: 30 seconds uncontested; progress pauses while contested and decays toward neutral after 15 seconds empty.
- Respawn wave: 10 seconds, followed by 10 seconds of base-exit invulnerability.
- Tie after time: owned outposts, then cumulative objective-control ticks, then a 90-second sudden-death fight for the center outpost; unresolved sudden death is a draw.
- Kill farming: no team point for repeatedly killing the same victim within 60 seconds; victim must have left spawn protection and participated in combat.
- Resources are virtual match-only balances. They cannot be dropped, traded, hopper-transferred, or retained after the match.
- Vanilla representations: tagged mobs for bosses/summons, interaction entities or marked blocks for structures, projectile/raycast siege, inventories for Armory and Storage, scoreboard for score/time/outposts, boss bar for active capture or boss health, action bar for resources and prompts.

## Castle Siege / War

### Source-faithful rules

- Scheduled protected battle; nominal roster is 50 attackers and 50 defenders.
- Attackers start at a war camp. Defenders start inside the fort.
- Attackers must capture all three exterior rally points A, B, and C before fort gates can take damage.
- Captured rally points cannot be retaken and become attacker wave-respawn and Armory locations.
- Defenders respawn only inside the fort.
- Attackers earn Battle Tokens through contribution and spend them at Armories.
- Defenders receive automatically generated Siege Supplies and spend them repairing gates, Armories, generators, and siege weapons.
- Attacker weapons: Cannon, Fire Launcher, and Repeater.
- Defender weapons: Ballista, Explosive Cannon, Repeater Turret, Fire Dropper, and Horn of Resilience.
- Both teams may use Inferno Mines and manually armed Powder Kegs.
- Attackers win only by breaching the fort and completing capture of the inner Claim.
- If the timer expires before the Claim is captured, defenders win. Kills and partial objectives are statistics, not tiebreakers.

### Minecraft adaptations and defaults

- Preparation: five minutes. Battle: 30 minutes. The duration is based on the best maintained gameplay reference because official public pages do not publish it.
- Rally capture: 30 seconds uncontested; any defender contests and pauses it. Progress decays after 15 empty seconds. Captured points are permanent.
- Claim capture: 30 seconds uncontested. There is no overtime; capture must finish before the deadline.
- Respawn waves: 20 seconds. Attackers use camp or captured rally points; defenders use fort spawns.
- Gate and siege health, ammunition, token income, supply generation, repair cost/rate, weapon cooldown, blast radius, and damage are explicit YAML balance values.
- Powder Kegs use a two-second visible fuse. Only the arming team can cancel by disarming; either team may destroy the keg. Resolution cancels all fuses and projectiles.
- Siege effects modify only registered structure health and players within arena bounds. Vanilla TNT, fire spread, liquids, pistons, hoppers, and arbitrary redstone are blocked.

## Commands and permissions

Root command: `/gamemodes` with aliases `/gmodes`, `/opr`, and `/siege`.

Player commands:

- `join <opr|siege> [arena]`
- `leave`
- `ready`
- `status`
- `team`

Admin commands (`gamemodes.admin`):

- `arena create|delete|setspawn|setregion|setobjective|setstructure|validate`
- `start <arena>`
- `stop <arena>`
- `reload`
- `debug <arena>`

Default permission `gamemodes.play` allows player commands. Admin mutations are logged.

## Configuration and validation

`config.yml` contains global storage, queue, recovery, UI, and safety settings. `arenas/*.yml` contains mode, template world, bounds, spawns, objective regions, structures, resources, siege placements, and balance overrides.

Validation rejects missing/unloaded templates, duplicate IDs, overlapping or out-of-bounds objectives, missing team spawns, negative durations/costs, impossible dependencies, unsupported schema versions, and writable paths outside the plugin data directory. One invalid arena is disabled without disabling valid arenas.

## Persistence and recovery

Each mutation increments a match sequence. The SQLite writer serializes updates and commits only if the expected previous sequence matches. Checkpoints contain mode state, roster, contributions, inventories/resources, entities/structures by logical ID, arena generation ID, and an epoch deadline. Restart reconstructs a monotonic remaining duration from the persisted epoch deadline and clamps negative values to immediate resolution.

Player snapshots are written before arena teleport. Restoration is idempotent and remains pending until confirmed on the main server thread, including after login. Reward rows have deterministic `(match_id, player_id, reward_type)` keys and move through pending/applied states so retries cannot duplicate internal grants.

## Verification and acceptance

- Gradle builds with JDK 25 against pinned Paper API `26.2.build.112-stable`.
- Plugin loads on a matching Paper server with no required dependencies.
- Automated rules tests cover every lifecycle transition, contested capture, score/deadline ordering, tie resolution, resource death/disconnect behavior, gate lock, rally permanence, Claim deadline, sequence/CAS recovery, and idempotent restoration/rewards.
- A smoke server exercises creation/validation, queue/preparation, one complete OPR match, one complete Siege match, disconnect/reconnect, abort, plugin shutdown/startup recovery, player restoration, and instance cleanup.
- Both modes expose all named objectives, upgrades, PvE/summons or siege/logistics, UI, victory conditions, and configurable defaults end to end.

## Non-goals

- Exact New World combat physics, proprietary UI, animations, maps, copyrighted assets, or undocumented tuning values.
- Client mods, mandatory resource packs, cross-server matchmaking, live-world territory ownership, settlement systems, or external economies.
- Supporting every historical New World patch or every Paper release.

## Rule sources

- [New World: PvP Vision and New Features](https://www.newworld.com/en-us/news/articles/pvp-vision-new-features)
- [New World gameplay overview](https://www.newworld.com/en-us/game/gameplay)
- [Season Two: Blood of the Sands](https://www.newworld.com/en-us/game/releases/season-two-blood-of-the-sands)
- [Season of the Divide](https://www.newworld.com/en-us/game/releases/season-of-the-divide)
- [Fight the World: War](https://www.newworld.com/en-us/news/articles/fight-the-world-war)
- [The Evolution of New World’s PvP](https://www.newworld.com/en-gb/news/articles/the-evolution-of-new-worlds-pvp)
- [Paper API repository metadata](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml)
