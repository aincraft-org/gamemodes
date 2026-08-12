# New World Gamemodes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Build a standalone JDK 25 Paper plugin with complete playable Outpost Rush and Castle Siege modes.

**Architecture:** A shared deterministic match coordinator owns lifecycle, exclusive player/arena allocation, persistence, recovery, and cleanup. Separate OPR and Siege engines implement mode rules behind a Paper adapter.

**Tech Stack:** Java 25, Gradle Kotlin DSL, Paper API `26.2.build.112-stable`, JUnit 5, SQLite JDBC, SnakeYAML Engine.

## Global Constraints

- No required client mod, resource pack, economy, permissions, world-management, or placeholder plugin.
- Domain rules remain independent of Bukkit where practical.
- One player belongs to at most one queue or match; teams differ by at most one.
- Match mutations are serialized; terminal order is event, score, victory, deadline.
- Templates clone only while unloaded; every instance has a generation ID and idempotent cleanup.
- Player state is captured before teleport and restored idempotently after disconnect or restart.
- SQLite checkpoints use sequence/CAS ordering and epoch deadlines; runtime clocks are monotonic.
- Rewards use deterministic keys and a transactional outbox.
- All balance values are YAML-configurable; undocumented values are labeled adaptations.

## Task 1: Build Foundation and Common Match Engine

**Files:** Build scripts, `plugin.yml`, `GamemodesPlugin`, `domain/common`, and focused tests.

**Interfaces:** Produce `MatchCoordinator`, `Match`, `MatchEvent`, `MatchResult`, `Deadline`, `RespawnWave`, and `PaperGateway`.

- [ ] Write failing tests for lifecycle, illegal transitions, balanced teams, quorum, queue exclusivity, disconnect reservations, and event ordering.
- [ ] Run focused tests; expect compilation failures before contracts exist.
- [ ] Implement immutable identifiers, phase validation, clocks, queue ownership, and serialized event dispatch.
- [ ] Run `./gradlew clean test`; expect all foundation tests to pass under JDK 25.

## Task 2: Persistence, Player Restoration, and Rewards

**Files:** `persistence/*`, `player/*`, `reward/*`, and `db/migrations/V1__initial.sql`.

**Interfaces:** Produce `PersistenceStore.save(expectedSequence, MatchSnapshot)`, `PlayerStateService.capture/restore`, and `RewardOutbox.enqueue/applyPending`.

- [ ] Test stale-sequence rejection, database reopen, epoch-deadline recovery, full player snapshot round-trip, pending-login restore, and reward retry idempotence.
- [ ] Implement serialized SQLite transactions, versioned snapshots, pending restores, and deterministic reward keys.
- [ ] Simulate crashes before/after outbox application and prove no duplicate or lost internal grant.
- [ ] Run persistence/player/reward tests; expect pass.

## Task 3: Configuration and Arena Instances

**Files:** `config/*`, `arena/*`, `config.yml`, and sample OPR/Siege arena definitions.

**Interfaces:** Produce `ArenaDefinition`, `ArenaCatalog`, and generation-tagged `ArenaInstanceManager`.

- [ ] Test missing templates/spawns/objectives, duplicate IDs, overlaps, bounds, negative values, unsafe paths, and schema versions.
- [ ] Implement strict YAML loading and per-arena disablement.
- [ ] Implement unloaded-template copying, reservations, generation IDs, and idempotent unload/delete.
- [ ] Run config/arena tests; expect pass.

## Task 4: Complete Outpost Rush Rules Engine

**Files:** Focused `domain/opr` types for capture, score, resources, structures, Baroness, portal, summons, waves, and results.

**Interfaces:** `OprMatch.handle(OprEvent, Instant)` returns ordered effects and snapshots.

- [ ] Test 20v20/quorum, party cap, three contested outposts, outpost/kill score, 1,000-point victory, deadline, and tie chain.
- [ ] Implement anti-farm victim cooldown, target-before-deadline ordering, and sudden death.
- [ ] Test resources, safe storage, 50% death/disconnect loss, Armory, gate/Command Post tiers, Ward, and repairs.
- [ ] Implement/test Baroness buffs and 60–150-second score lock.
- [ ] Implement/test five-minute portal and Bear/Specter/Brute summons.
- [ ] Implement synchronized waves and reconnect behavior.
- [ ] Run all OPR tests; expect pass.

## Task 5: Complete Castle Siege Rules Engine

**Files:** Focused `domain/siege` types for rosters, rallies, gates, Claim, tokens/supplies, repairs, weapons, kegs, waves, and results.

**Interfaces:** `SiegeMatch.handle(SiegeEvent, Instant)` returns ordered effects and snapshots.

- [ ] Test 50v50/quorum, preparation, permanent contested rallies, forward attacker spawns, and fort-only defender spawns.
- [ ] Implement/test gate immunity until all rallies are captured.
- [ ] Implement contribution-earned Battle Tokens and generated Siege Supplies with atomic spend/repair.
- [ ] Implement all attacker/defender weapons, mines, and two-second kegs with quotas, cooldowns, bounds, and cleanup.
- [ ] Implement/test 30-second Claim and strict 30-minute deadline with no overtime.
- [ ] Run all Siege tests; expect pass.

## Task 6: Paper Adapter, Commands, UI, and Safety

**Files:** `paper`, `listener`, `command`, `ui`, bootstrap, and plugin resources.

- [ ] Test join/leave/ready/status/team and admin arena/start/stop/reload/debug commands and permissions.
- [ ] Implement scoreboards, boss bars, action bars, Armory/Storage inventories, and tagged entities/interactions.
- [ ] Block unauthorized block changes, TNT/fire/liquids/redstone/hoppers, transfer, teleports, portals, vehicles, pearls, chorus, beds, respawns, outsiders, and cross-team access.
- [ ] Prove all tasks/projectiles/entities/fuses/UI are generation-tagged and cleaned on finish/abort/shutdown.
- [ ] Run Paper adapter tests; expect pass.

## Task 7: Recovery and End-to-End Verification

- [ ] Add complete reduced-duration OPR and Siege integration scenarios with production rules and test clocks.
- [ ] Add restart tests for every active phase, reconnect, abort, overdue deadline, pending restore/reward, and stale generation.
- [ ] Run `./gradlew clean test`; expect all tests to pass.
- [ ] Run `./gradlew jar`; verify metadata, migration, defaults, and compile-only Paper dependency.
- [ ] Launch matching Paper, exercise both modes, disconnect/restart recovery, restoration, rewards, and instance cleanup.

## Completion Gate

- [ ] Both modes are playable end to end with every named mechanic from the design.
- [ ] No placeholders, silent fallback rules, required integrations, or unsupported source claims remain.
- [ ] Full build, focused tests, integration tests, and Paper smoke scenarios pass.
