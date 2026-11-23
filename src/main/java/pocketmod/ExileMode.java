package pocketmod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public final class ExileMode {
    public static final String ROOT = "pocketmod_exile";
    public static final String PURSUER = "pm_exile_pursuer";
    private static WorldType worldType;

    private ExileMode() {}

    public static void register() {
        worldType = new WorldType("exile");
        net.minecraftforge.fml.common.registry.GameRegistry.registerWorldGenerator(
                new ExileTrials(), 100);
    }

    public static WorldType worldType() {
        return worldType;
    }

    public static NBTTagCompound state(EntityPlayer player) {
        NBTTagCompound root = PlayerData.root(player);
        if (!root.hasKey(ROOT, 10)) root.setTag(ROOT, new NBTTagCompound());
        return root.getCompoundTag(ROOT);
    }

    public static boolean enabled(World world) {
        return world != null
                && !world.isRemote
                && (world.provider.getDimension() == 0 || world.getMinecraftServer() != null)
                && ExileWorldData.get(world).enabled;
    }

    public static boolean active(EntityPlayer player) {
        return enabled(player.world) && !state(player).getBoolean("cured");
    }

    public static void progress(EntityPlayer player, String achievement, double amount) {
        if (!active(player)) return;
        NBTTagCompound s = state(player);
        if (achievement != null) {
            String key = "done_" + achievement;
            if (s.getBoolean(key)) return;
            s.setBoolean(key, true);
        }
        s.setDouble("progress", Math.min(100, s.getDouble("progress") + amount));
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || event.world.isRemote
                || event.world.provider.getDimension() != 0
                || event.world.getTotalWorldTime() % 20 != 0) return;
        ExileWorldData data = ExileWorldData.get(event.world);
        if (!data.enabled) return;
        data.seconds++;
        data.markDirty();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP p = (EntityPlayerMP) event.player;
        ExileBloodthirst.refreshModifiers(p);
        if (!enabled(p.world)) return;
        ExileProgression.enforce(p);
        if (p.ticksExisted % 20 != 0) return;
        ExileTrials.tick(p);
        NBTTagCompound s = state(p);
        s.setBoolean("enabled", true);
        if (!s.getBoolean("verdict")) {
            s.setBoolean("verdict", true);
            NetworkHandler.sendPrompt(p, 2, 0, 0);
        }
        if (!active(p)
                || p.isDead
                || p.getHealth() <= 0
                || p.isCreative()
                || (p.isSpectator() && !Spector.isActive(p))) return;
        if (PocketDimensionManager.isPocketDimension(p.dimension)) {
            s.setInteger("stage", 0);
            s.setInteger("contamination", 0);
            int spent = s.getInteger("shelter") + 1;
            s.setInteger("shelter", spent);
            s.setInteger("outside", 0);
            if (spent == Math.max(1, ExileConfig.pocketSeconds - 180))
                message(p, "shelter_warning");
            if (spent >= ExileConfig.pocketSeconds) {
                PocketTeleporter.toggle(p);
                message(p, "shelter_expired");
            }
            return;
        }
        int outside = Math.min(ExileConfig.pocketCooldown, s.getInteger("outside") + 1);
        s.setInteger("outside", outside);
        if (outside >= ExileConfig.pocketCooldown) s.setInteger("shelter", 0);
        ExileWorldData data = ExileWorldData.get(p.world);
        int cx = MathHelper.floor(p.posX) >> 4;
        int cz = MathHelper.floor(p.posZ) >> 4;
        // Общая местная область не позволяет сбросить застой шагом через границу чанка.
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) data.visit(p.dimension, cx + dx, cz + dz);
        double exposure = data.exposure(p.dimension, cx, cz);
        int stage = stage(exposure);
        if (stage >= 3) {
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == 2)
                        data.contaminate(p.dimension, cx + dx, cz + dz, 0.25);
        }
        if (stage > s.getInteger("stage")) message(p, "stage_" + stage);
        s.setInteger("stage", stage);
        s.setInteger(
                "contamination", (int) Math.min(100, exposure * 100 / ExileConfig.fullSeconds));
        ExileBloodthirst.advance(p);
        int thirst = s.getInteger("thirst");
        if ((stage > 0 || thirst >= ExileConfig.thirstSeconds)
                && data.seconds % ExileConfig.spawnInterval == 0)
            ExileEncounters.spawn(p, Math.max(1, stage));
    }

    public static int stage(double exposure) {
        if (exposure >= ExileConfig.fullSeconds) return 4;
        if (exposure >= ExileConfig.witherSeconds) return 3;
        if (exposure >= ExileConfig.fleeSeconds) return 2;
        if (exposure >= ExileConfig.firstSeconds) return 1;
        return 0;
    }

    public static boolean canEnterShelter(EntityPlayerMP p) {
        if (!active(p) || state(p).getInteger("shelter") < ExileConfig.pocketSeconds) return true;
        message(p, "shelter_cooldown");
        return false;
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.player instanceof EntityPlayerMP
                && !PocketDimensionManager.isPocketDimension(e.toDim))
            progress(e.player, "dimension_" + e.toDim, 5);
    }

    public static void message(EntityPlayerMP player, String key) {
        player.sendStatusMessage(new TextComponentTranslation("pocketmod.exile." + key), false);
    }
}
