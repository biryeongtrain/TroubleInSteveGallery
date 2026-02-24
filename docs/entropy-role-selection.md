# Entropy-Based Role Selection

This server selects `DETECTIVE` first and then `TRAITOR` using per-player entropy.

## Persistence

- Entropy is in-memory only and is not persisted to `tts/playerData/<uuid>.json`.
- Entropy resets naturally when the server restarts.

## Config

`config/ttt/config.json` supports:

- `roleEntropyGainMin` (default: `10`)
- `roleEntropyGainMax` (default: `35`)

At round end, each round participant gains a random entropy value within this range.

## Runtime Rules

- On player join, entropy is ensured to be at least the baseline (`100`).
- Role selection order:
  1. detective candidates are selected from highest entropy players.
  2. traitor candidates are selected next from the remaining highest entropy players.
- If multiple players share the same highest entropy, one is picked randomly among that tied group.
- Players selected as detective or traitor have entropy reset to baseline (`100`).

## Operator Command

- `/tts_admin reset_entropy`
- `/tts_admin reset_entropy all`
- `/tts_admin reset_entropy <players>`

These commands reset the selected online players' role entropy to baseline (`100`).
