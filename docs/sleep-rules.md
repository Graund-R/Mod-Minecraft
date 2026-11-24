# Exile bed rules

These changes apply to the Exile world mode. Ordinary-world sleep remains vanilla when Exile is disabled. Pocket beds retain their old prohibition in normal mode.

Sleep requires the existing Exile night interval, local contamination strictly below 50%, and bloodthirst strictly below 75%. Both halves of vanilla beds and the player's column count for contamination. Sleeping is denied within 16 horizontal and 8 vertical blocks of a tagged pursuer, during an active trial, or within 64 horizontal blocks of a living boss on any floor. Vanilla distance, occupancy and ordinary monster checks still run. Thresholds and maximum recovery are available through the Exile settings.

Pocket bed activation runs the vanilla interaction with a scoped surface-world permission. Outside that synchronous call the pocket stays non-surface, with its original constant lighting. Existing guest/build permissions and canceled interactions remain effective. Sleep uses the outside clock. Sleeping in one private pocket cannot skip the night while another player in the outside world or another pocket is awake. Ordinary spectators are excluded, following vanilla behavior. Other dimensions retain their own rules.

Only a completed night can restore health. Maximum recovery is four health points (two hearts). Each restored health point costs one food point, rounded up; three drumsticks are retained. A player without spare food can sleep but receives no recovery. Health recovery respects the missing-health limit and canceled or reduced healing from other mods. One reward is allowed per night, with the record saved on the player. Leaving the bed early, breaking it, becoming unsafe or re-entering during the same night does not duplicate recovery.

Bed spawn-point updates are canceled in Exile. Forced spawn updates and non-bed spawn sources retain their existing behavior; the resurrection-totem system is unchanged.

The night coordinator advances daylight only after all relevant players are fully asleep. It does not advance total world ticks or the separate infection clock. Bloodthirst advancement is skipped while sleeping. Local contamination and shelter time continue to count actual elapsed ticks, without applying skipped night hours. Sleep does not refill shelter duration or its cooldown. A player whose shelter expires is awakened before being returned outside.

Validation uses real vanilla bed entry/wakeup with direct Forge handler dispatch in in-memory worlds. No Minecraft client or server process is started. Checks cover threshold boundaries, chunk seams, pursuers, bosses on another floor, food payment, interrupted and repeated sleep, spawn preservation, pocket entry, guest cancellation, frozen daylight, shared dawn, unchanged infection clocks and normal-mode compatibility.

The proposed cyclical bloodthirst formula remains a separate design; this bed change uses the current stored thirst scale.
