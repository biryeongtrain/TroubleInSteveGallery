# Kill/Point Save E2E Checklist

This checklist validates kill handling, point/karma updates, and persistence flow.

## 1. Pre-check

1. Start server:
   - `./gradlew.bat runServer --no-daemon`
2. Prepare enough players:
   - Real clients or `tts_debug fake_player`
3. Backup existing data:
   - `tts/playerData/*.json`
   - `tts/roundData/**/*`

## 2. Common observations

1. In-game behavior:
   - Point changes after kills
   - Karma depletion auto-elimination
2. Persisted files:
   - `tts/playerData/<uuid>.json`
   - `tts/roundData/<date>/<uuid>.json`
3. Logs:
   - Kill logs
   - Save/load warnings or errors

## 3. Scenario A: clean kill (enemy kill)

1. Start a round and set attacker/victim as enemy teams.
2. Attacker kills victim.
3. Expected:
   - Attacker gets kill points (`+2`).
   - Attacker gets karma bonus (`clean_kill`).
   - Victim is switched to spectator.
   - Both attacker and victim round kill data are recorded.

## 4. Scenario B: team kill (friendly fire kill)

1. Use same-team pair (`innocent-detective` or `traitor-traitor`).
2. Friendly kill occurs.
3. Expected:
   - No kill points for attacker.
   - Karma penalty applied (`friendly_kill`).
   - If karma reaches `0`, attacker is auto-eliminated.
   - Victim is switched to spectator.

## 5. Scenario C: leave during active round

1. A non-spectator leaves while round is active.
2. Expected:
   - Leave result is appended as `LOSE` with `gainPoints=0`.
   - `tts/playerData/<uuid>.json` is saved immediately.
   - Player is removed from participants/alive sets.

## 6. Scenario D: reconnect flow after leaving

1. Case 1: reconnect while same round is still active.
2. Expected:
   - Join as spectator.
   - Teleport to current round world.

3. Case 2: reconnect while another round is active.
4. Expected:
   - Join as spectator.
   - Teleport to current active game world.

5. Case 3: reconnect while no round is active.
6. Expected:
   - Join as adventure.
   - Teleport to lobby/spawn world.

## 7. Scenario E: round-end persistence

1. End round normally or run `tts_admin stop`.
2. Expected:
   - `tts/playerData/<uuid>.json` includes accumulated round results.
   - `tts/roundData/<date>/<uuid>.json` is created.
   - JSON structure remains compatible:
     - playerData: `uuid`, `results`
     - roundData: `date`, `role`, `roundKillData`

## 8. Pass criteria

1. All Scenario A-E expectations are satisfied.
2. No missing/invalid JSON in saved files.
3. No server exception logs in kill/point/leave/reconnect paths.
