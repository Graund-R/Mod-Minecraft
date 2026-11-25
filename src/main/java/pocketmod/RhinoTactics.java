package pocketmod;

/** Server-side patience, independent of rendering and navigation. Times are Minecraft ticks. */
public final class RhinoTactics {
    public static final int COLLAPSE_COOLDOWN = 260;
    private int frustration, cooldown, attempts;
    public void tick(boolean elevated, boolean choosing) {
        if (cooldown > 0) cooldown--;
        if (elevated && choosing) frustration = Math.min(300, frustration + 1);
        else if (!elevated) { frustration = Math.max(0, frustration - 3); attempts = 0; }
    }
    public void attemptedHorn() { attempts++; }
    public boolean shouldCollapse(boolean elevated) {
        return elevated && cooldown == 0 && frustration >= 180 && attempts >= 2;
    }
    public void collapsed() { frustration = 0; attempts = 0; cooldown = COLLAPSE_COOLDOWN; }
    public int frustration() { return frustration; }
    public int cooldown() { return cooldown; }
    public void restore(int irritation, int delay) {
        frustration = Math.max(0, Math.min(300, irritation));
        cooldown = Math.max(0, Math.min(COLLAPSE_COOLDOWN, delay));
        attempts = 0;
    }
    public static boolean inHornArc(double forward, double lateral, double vertical) {
        return forward > -0.4 && forward < 4.4 && Math.abs(lateral) < 2.6 && Math.abs(vertical) < 3.2;
    }
}
