package pocketmod;

/** Параметры уровня волны, используемые сервером и магазином. */
final class WaveLevel {
    final double radius;
    final float damage;
    final double knockback;
    final double manaCost;
    final long cooldownTicks;
    final int slowTicks;
    final int slowAmplifier;
    final int nauseaTicks;
    final int weaknessTicks;

    WaveLevel(int level) {
        int index = Math.max(1, Math.min(AbilityType.WAVE.maxLevel, level)) - 1;
        radius = PocketConfig.waveRadius[index];
        damage = (float) PocketConfig.waveDamage[index];
        knockback = PocketConfig.waveKnock[index];
        manaCost = PocketConfig.waveCost[index];
        cooldownTicks = Math.round(PocketConfig.waveCooldown[index] * 20.0);
        slowTicks = (int) Math.round(PocketConfig.waveSlowSec[index] * 20.0);
        slowAmplifier = (int) Math.round(PocketConfig.waveSlowAmp[index]);
        nauseaTicks = (int) Math.round(PocketConfig.waveNauseaSec[index] * 20.0);
        weaknessTicks = (int) Math.round(PocketConfig.waveWeakSec[index] * 20.0);
    }
}
