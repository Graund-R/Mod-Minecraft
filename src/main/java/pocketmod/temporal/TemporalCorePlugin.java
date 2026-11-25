package pocketmod.temporal;

import java.util.Map;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

/** Small 1.12.2 tick gates. No classes belonging to the original Stasis mod are used. */
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.Name("PocketTemporal")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({"pocketmod.temporal.TemporalCorePlugin", "pocketmod.temporal.TemporalTransformer", "pocketmod.temporal.MorphCompatibilityTransformer"})
public final class TemporalCorePlugin implements IFMLLoadingPlugin {
    public static boolean loaded;
    public TemporalCorePlugin() { loaded = true; }
    public String[] getASMTransformerClass() { return new String[] {"pocketmod.temporal.TemporalTransformer", "pocketmod.temporal.MorphCompatibilityTransformer"}; }
    public String getModContainerClass() { return null; }
    public String getSetupClass() { return null; }
    public void injectData(Map<String, Object> data) {}
    public String getAccessTransformerClass() { return null; }
}
