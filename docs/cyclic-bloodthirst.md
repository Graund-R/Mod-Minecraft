# Cyclic bloodthirst

The cyclic model is enabled by default. The older simple and context models remain available through configuration and the F8 model button, with independently retained progress.

## Agreed starting values

The precise scale is 0–100. Habit is active seconds since the last distinct boss victory divided by 1800, capped at six. An ordinary kill lowers the bar but does not reset habit.

Outside calm, nominal points per minute are:

`10 × (1 + min(0.50, 0.05 × distinct bosses)) × (1 + 0.15 × habit)`

Fill is multiplied by a random target in 0.85–1.15. New targets are chosen every 60–120 active seconds, with a ten-second linear transition. Night has no additional multiplier in this model. The older test model retains its own nighttime rules.

Relief from an eligible victim is:

`100 / (3 + habit) × danger × random(0.9, 1.1)`

Danger is `sqrt(maxHealth / 20) × (potentialAttack / 4)^0.7 × (1 + armor / 40)`, clamped to 0.25–4. Potential attack uses the mob attribute and the largest observed raw attack before player armor or shield mitigation. Ranged enemies have a conservative fallback; creepers and ghasts have explosion/fireball estimates. Boss repeat relief uses the maximum weight. Safe passive animals and decoys grant no relief. Real combat participation or a direct dangerous kill is required; stale contributions do not credit later automated kills. Taking health damage is not required.

Once the bar has reached 40, hunting it below 15 grants 45–75 active seconds of calm at one quarter of the normal fill rate. Killing at zero cannot extend calm. A new wave must reach 40 before another ordinary calm period can trigger.

## Bosses and pause rules

Each player's first credited defeat of a boss type clears thirst and habit and grants 180 active seconds of calm. A repeat defeat still gives strong weighted relief, retaining habit and the existing distinct-boss count. Registered entity IDs identify boss types; a stable `pm_exile_boss_id` entity tag can distinguish future custom variants. Boss achievements from old saves migrate into this history.

Current arena detection uses active trials and the living undefeated boss's 64-block horizontal neighborhood, across the full vertical column, matching bed safety. Global saved defeat history prevents a repeat summon from restoring protection. This is the boundary available for the current egg-summoned boss; future generated arenas can supply more precise boundaries.

Sleeping, pocket shelter, protected boss fights, creative mode, ordinary spectator mode, cure and the explicit F8 pause freeze active accumulation. This includes habit, calm remaining, and rate-change clocks. No offline or skipped-night time is added. Full thirst still uses the existing frenzy, hunger drain, audiovisual cues and half-heart damage every two seconds. Protection suppresses thirst damage, frenzy and screen/heartbeat effects while retaining the stored bar.

## Saved state and testing

The exact bar, three independent random streams (fill/relief/calm), random targets and transition progress, habit, calm and boss history are stored with the player. Reloading preserves the next outcomes. Hunting does not reroll fill targets. World boss-defeat history is stored separately with the world. The legacy integer bar remains canonical for existing bed thresholds and warning/damage code, without reaching its maximum before the precise bar reaches 100.

The F8 page shows the precise bar, habit, effective rate, ordinary relief, calm time and distinct bosses. Its controls work for the new model; the nighttime override is limited to the older simple model.

Production-logic tests cover formulas, limits, first/repeated boss rewards, migration, compressed-NBT continuation, independent random streams, smoothness, fast-time equivalence, pause rules, blocked attacks, cross-mod attribute scoring, passive-animal exclusion, combat contribution, protected arenas, model switching, HUD data and frenzy/damage integration. They use in-memory worlds and entity classes without starting Minecraft. Balance still requires player testing; the earlier simulation's parameters are preserved rather than silently retuned.
