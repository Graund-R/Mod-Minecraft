package pocketmod;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.AbstractSkeleton;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
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
        double power = 0;
        for (AbilityType type : AbilityType.values()) power += AbilityStorage.getLevel(p, type);
        for (int i = 0; i < BodyEffects.IDS.length; i++) power += BodyEffects.perm(p, i);
        power = Math.max(s.getDouble("power"), power + s.getDouble("progress") / 5);
        s.setDouble("power", power);
        int increment = 1 + (int) (Math.min(40, power) / 10);
        int previousThirst = s.getInteger("thirst");
        int thirst = Math.min(ExileConfig.thirstSeconds * 2, previousThirst + increment);
        s.setInteger("thirst", thirst);
        if (previousThirst < ExileConfig.thirstSeconds / 2
                && thirst >= ExileConfig.thirstSeconds / 2) message(p, "thirst_warning");
        if (previousThirst < ExileConfig.thirstSeconds && thirst >= ExileConfig.thirstSeconds)
            message(p, "thirst_severe");
        // Только короткие эффекты: после боя они исчезнут сами и не снимут зелья других модов.
        if (thirst >= ExileConfig.thirstSeconds / 2)
            p.addPotionEffect(new PotionEffect(MobEffects.HUNGER, 45, 0, true, false));
        if (thirst >= ExileConfig.thirstSeconds) {
            p.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 45, 0, true, false));
            p.addPotionEffect(new PotionEffect(MobEffects.MINING_FATIGUE, 45, 0, true, false));
        }
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

    private static boolean threat(EntityLiving mob, EntityPlayerMP p) {
        if (!(mob instanceof IMob) || mob.getAttackTarget() != p || mob.getDistanceSq(p) > 1024)
            return false;
        if (mob.getMaxHealth()
                < Math.max(
                        10, p.getMaxHealth() * 0.25 + ExileMode.state(p).getDouble("power") * 0.4))
            return false;
        if (mob instanceof AbstractSkeleton && mob.canEntityBeSeen(p)) return true;
        net.minecraft.pathfinding.Path path = mob.getNavigator().getPathToEntityLiving(p);
        return path != null
                && path.getFinalPathPoint() != null
                && path.getFinalPathPoint()
                                .distanceTo(
                                        new net.minecraft.pathfinding.PathPoint(
                                                MathHelper.floor(p.posX),
                                                MathHelper.floor(p.posY),
                                                MathHelper.floor(p.posZ)))
                        <= 2;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent e) {
        if (e.isCanceled()
                || e.getAmount() <= 0
                || !(e.getEntityLiving() instanceof EntityLiving)
                || !(e.getSource().getTrueSource() instanceof EntityPlayerMP)) return;
        EntityPlayerMP p = (EntityPlayerMP) e.getSource().getTrueSource();
        EntityLiving mob = (EntityLiving) e.getEntityLiving();
        if (!active(p) || !threat(mob, p)) return;
        NBTTagCompound n = mob.getEntityData();
        // Участие хранится на самом противнике, список сущностей в памяти сервера не растёт.
        String key = "pm_fight_" + p.getUniqueID();
        n.setFloat(key, n.getFloat(key) + Math.min(e.getAmount(), mob.getHealth()));
        n.setLong(key + "_time", mob.world.getTotalWorldTime());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent e) {
        if (e.isCanceled()
                || e.getEntityLiving().world.isRemote
                || !(e.getEntityLiving() instanceof EntityLiving)) return;
        EntityLiving mob = (EntityLiving) e.getEntityLiving();
        if (!enabled(mob.world)) return;
        for (EntityPlayer player : mob.world.playerEntities) {
            if (!(player instanceof EntityPlayerMP)
                    || !active(player)
                    || player.getDistanceSq(mob) > 4096) continue;
            String key = "pm_fight_" + player.getUniqueID();
            float damage = mob.getEntityData().getFloat(key);
            long last = mob.getEntityData().getLong(key + "_time");
            if (damage < Math.min(10, mob.getMaxHealth() * 0.3)
                    || mob.world.getTotalWorldTime() - last > 600) continue;
            NBTTagCompound s = state(player);
            int relief =
                    (int)
                            Math.min(
                                    ExileConfig.combatRelief,
                                    ExileConfig.combatRelief
                                            * mob.getMaxHealth()
                                            / Math.max(20, player.getMaxHealth()));
            s.setInteger("thirst", Math.max(0, s.getInteger("thirst") - relief));
            // Боссы и достижения дают одноразовый рост, обычные бои ограничены текущей ступенью.
            if (!mob.isNonBoss())
                progress(player, "boss_" + net.minecraft.entity.EntityList.getKey(mob), 10);
            else if (s.getDouble("progress") < 10) progress(player, null, 0.1);
            ExileTrials.defeated((EntityPlayerMP) player, mob);
            if (!mob.getEntityData().hasKey("pm_exile_trial")
                    && player.getRNG().nextFloat() < ExileConfig.discoveryChance)
                ExileProgression.discover((EntityPlayerMP) player);
            message((EntityPlayerMP) player, "combat_relief");
        }
    }

    public static void message(EntityPlayerMP player, String key) {
        player.sendStatusMessage(new TextComponentTranslation("pocketmod.exile." + key), false);
    }
}
