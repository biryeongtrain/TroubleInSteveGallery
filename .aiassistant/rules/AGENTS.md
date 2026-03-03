---
적용: 항상
---

# TroubleInSteveGallery AGENT GUIDE

This document defines repository-wide rules for Codex and other automated contributors.
Scope: entire repository unless a deeper `AGENTS.md` overrides this file.

## 0) Mission and Priorities

- Primary mission: keep game rounds stable on dedicated servers while implementing requested changes safely.
- Prioritize gameplay compatibility, runtime reliability, and maintainability over broad refactors.
- Prefer minimal, reversible patches that preserve existing behavior unless a behavior change is explicitly requested.

---

## 1) Project Baseline

- Target runtime: Minecraft `1.21.8` (current `fabric.mod.json` also declares compatibility with `1.21.7`).
- Mod loader and API: Fabric Loader + Fabric API.
- Language: Java `21`.
- Build system: Gradle Wrapper (`gradlew`, `gradlew.bat`).
- Main verification entry points:
    - `.\gradlew.bat build` (or `./gradlew build`)
    - `.\gradlew.bat runServer` (or `./gradlew runServer`)
    - `.\gradlew.bat runDatagen` (or `./gradlew runDatagen`)
    - `.\gradlew.bat runGameTest` (or `./gradlew runGameTest`) when GameTests exist or are added
- Primary entrypoints:
    - `kim.biryeong.ttt.TroubleInTerroristTownMod`
    - `kim.biryeong.ttt.datagen.PolymerTemplateModDataGenerator`

---

## 2) Package Responsibilities

### MUST
- Respect current package boundaries:
    - root package (`kim.biryeong.ttt`): bootstrap and event wiring only.
    - `game/manager/*`: authoritative game state, phase transitions, round flow, and win/loss orchestration.
    - `game/data/*`: codec-backed data models and serialization shape for player/round history.
    - `player/duck/*`: player extension contracts only (implemented via mixins).
    - `mixin/*`: injection/accessor glue and persistence hooks; keep logic minimal and targeted.
    - `world/*`, `world/gen/*`: runtime world/map-template generation and player spawning.
    - `command/*`: Brigadier command trees, permissions, and actionable operator feedback.
    - `item/*`, `block/*`, `entity/*`: registry and feature implementation for content.
    - `datagen/*`: generated models/translations providers only.
    - `util/*`: reusable helpers without feature-specific side effects.
    - `config/*`: configuration schema and load/save policy.
- Keep server-authoritative decisions in managers, not in UI/mixin layers.
- Treat `InGamePlayerInfoProvider` and `InGameEventProvider` as stable contracts between gameplay logic and mixin-backed player state.

### FORBIDDEN
- Do not move business rules from `game/manager` into mixins unless injection constraints require it.
- Do not write ad-hoc role/point/phase state outside established manager/provider paths.
- Do not manually edit generated output under `src/main/generated` for routine model/translation changes; regenerate via datagen.

---

## 3) Dependency and Boundary Rules

### MUST
- Keep dedicated-server safety: avoid client-only assumptions in runtime gameplay paths.
- Keep delayed game actions on server thread using existing scheduler patterns (`Scheduler`).
- Isolate compatibility/integration-specific behavior (for external mods/libraries) from core round logic when adding new integrations.
- Preserve existing runtime-world approach (`Fantasy`, map templates, custom chunk generator) for TTT maps.

### FORBIDDEN
- Do not bypass `GameManager`/`GameInstanceManager` for phase, role, alive-state, or win-condition mutations.
- Do not introduce hard dependency coupling in generic code paths when integration can be optional.

---

## 4) Data and Serialization Policy

### MUST
- Preserve codec-backed schema and field names unless a breaking change is explicitly requested:
    - `config/ttt/config.json` via `Config.CODEC`
    - `tts/playerData/<uuid>.json` via `PlayerDataInstance.CODEC`
    - `tts/roundData/<date>/<uuid>.json` via `PlayerRoundDataInstance.CODEC`
- Keep persistence keys stable:
    - player custom data key `tts$loadout`
    - role/result/date codec strings and existing enum serialization behavior
- Keep map-template resource compatibility:
    - built-in IDs in code (currently includes `ttt:kitchen`, `ttt:lobby`)
    - additional maps from `Config.additionalMaps`
    - map template resources under `data/ttt/map_template`
- Keep defaults deterministic when files are missing or corrupted, and log context clearly.

### FORBIDDEN
- Do not silently change JSON shape or rename existing keys/paths used by saved data.
- Do not replace codec-based parsing with ad-hoc string parsing for existing data paths.

---

## 5) Mixins and Runtime World Policy

### MUST
- Keep mixin targets narrow, explicit, and purpose-specific.
- Add short intent comments when injection behavior is non-obvious or safety-critical.
- Re-verify startup and core round flow after changing any mixin.
- Preserve runtime-world safeguards in block/update behavior (`RuntimeWorld` checks in block-related mixins).

### FORBIDDEN
- No broad or speculative injections.
- No new mixin behavior that changes non-TTT vanilla behavior globally without clear justification.

---

## 6) Commands, Events, and Lifecycle

### MUST
- Keep command behavior explicit and errors actionable.
- Treat command names, permissions, and basic semantics as compatibility-sensitive:
    - `tts_admin` (`start`, `stop`, `set_role`, `give_points`, `reload`)
    - `tts` (`spectator_mode`)
    - `tts_debug` (`explode`, `debugmode`, `fake_player`)
    - `tts_debug fake_player` (`spawn [name] [count]`, `remove <name>`, `clear`, `list`)
- Preserve startup/lifecycle ordering:
    - bootstrap registrations in mod init
    - server reference and map loading on lifecycle callbacks
    - event registration semantics in `Events.registerEvents`
- Keep game phase transitions coherent (`NOT_STARTED`, `INITIALIZE`, `POST_GAME`, `MIDDLE_GAME`, `OVER_TIME`, `END_GAME`).

### FORBIDDEN
- Do not add client-required runtime behavior to server entrypoints unless explicitly requested.
- Do not change permission gates for operator-only commands without explicit request.

---

## 7) Code Style and Implementation Rules

### MUST
- Follow local style of touched files (imports, naming, formatting, logging pattern).
- Keep diffs scoped strictly to the requested task.
- Prefer small additive changes over broad rewrites.
- Add/update Javadoc for touched public members when behavior contracts are non-trivial.

### RECOMMENDED
- Reuse existing helpers/utilities before creating new ones.
- Keep immutable/value-style data carriers where practical.

### FORBIDDEN
- No drive-by refactors or unrelated cleanup in the same patch.

---

## 8) Exception and Logging Policy

### MUST
- Fail with actionable context for critical operations (map load, data read/write, command actions).
- Include identifiers in logs when relevant (`uuid`, player name, map id, resource id/path).
- Keep fallback behavior explicit when recovery is possible.

### FORBIDDEN
- No swallowed exceptions without logging.
- No vague error messages that omit the failing operation context.

---

## 9) Backward Compatibility Policy

### MUST
- Treat the following as compatibility-sensitive:
    - command names/arguments/permission requirements
    - role assignment/win condition/overtime/point behavior
    - serialized codec schemas and persistence keys
    - registry identifiers and resource paths (`ttt:*`)
    - map template IDs and spawn metadata assumptions
- For unavoidable breaking changes, document impact and migration path in change summary.

---

## 10) Dependency Introduction Policy

### MUST
- Prefer existing project dependencies and JDK APIs.
- Any new dependency requires explicit justification:
    - why existing stack is insufficient
    - scope and affected modules
    - maintenance/runtime impact

### FORBIDDEN
- Do not add overlapping libraries for trivial utility features.

---

## 11) Testing and Verification Policy

### MUST
- Run checks relevant to touched areas and report exactly what was run:
    - compile/build baseline: `build`
    - runtime/lifecycle/mixin/world changes: `runServer` spot checks
    - generated assets/translations/models changes: `runDatagen`
    - gameplay test flows: `runGameTest` when tests are available or newly added
- Prefer `runGameTest` first for gameplay verification when it can validate the change without a long-running server.
- When stopping verification tasks, terminate only processes started for the current task/session (track PID/process handle when launching).
- If verification cannot be performed, state limitations explicitly.

### RECOMMENDED
- Add focused GameTests for non-trivial gameplay logic changes.
- Keep generated resources in sync when datagen-affecting code is changed.

### FORBIDDEN
- Do not terminate unrelated `java`/`gradle` processes on the machine.
- Do not use broad kill patterns that can stop all Java processes when cleaning up test runs.

---

## 12) Documentation and Change Hygiene

### MUST
- Update docs when changing command behavior, data format, or required operator workflow.
- Keep summaries practical: what changed, why, compatibility impact, validation results.
- Never revert unrelated user-authored changes.

---

## 13) Existing Code Precedence

### MUST
- Existing repository behavior and established local conventions take precedence over generic style guidance.
- If this guide conflicts with hard code constraints, preserve runtime behavior first and note the mismatch in your summary.

---

## 14) Final Operational Checklist

Before finishing, verify:
- scope is minimal and task-aligned
- package boundaries are respected
- compatibility-sensitive behavior is preserved or clearly documented
- relevant checks were run (or limitations were reported)
- summary includes rationale, risk notes, and validation results
