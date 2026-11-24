package pocketmod;

/** Two-second, bounded transition; deliberately independent from the player's current form. */
final class ExileAtmosphereEnvelope {
    static float step(float current,float target) {
        target=Math.max(0,Math.min(1,target));
        return target>current?Math.min(target,current+0.025f):Math.max(target,current-0.025f);
    }
    private ExileAtmosphereEnvelope() {}
}
