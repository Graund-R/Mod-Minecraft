package pocketmod;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.network.ByteBufUtils;

/** Клиент отправляет намерения, сервер выполняет действия и синхронизирует данные игрока. */
public final class NetworkHandler {
    private static final String CHANNEL = "pocketmod";
    public static final SimpleNetworkWrapper CHANNEL_INSTANCE =
            NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
    private NetworkHandler() {}

    public static void register() {
        CHANNEL_INSTANCE.registerMessage(ActionHandler.class, ActionMessage.class, 1, Side.SERVER);
        PocketMod.proxy.registerClientMessages();
        MinecraftForge.EVENT_BUS.register(NetworkHandler.class);
    }

    public static void action(NetAction action, int a, int b, double value, String text) {
        CHANNEL_INSTANCE.sendToServer(new ActionMessage(action, a, b, value, text));
    }

    public static void sync(EntityPlayerMP player) {
        NBTTagCompound snapshot = PlayerData.root(player).copy();
        SyncedState.write(player, snapshot);
        CHANNEL_INSTANCE.sendTo(new SyncMessage(snapshot), player);
    }

    /** Передаёт клиенту WorldSavedData карманного измерения для подсказок и локального UI. */
    public static void syncPocket(EntityPlayerMP player) {
        if (player == null || !PocketDimensionManager.isPocketDimension(player.dimension)) return;
        NBTTagCompound snapshot = PocketData.get(player.world).writeToNBT(new NBTTagCompound());
        CHANNEL_INSTANCE.sendTo(new PocketSyncMessage(player.dimension, snapshot), player);
    }

    public static void syncPocketWorld(WorldServer world) {
        if (world == null || !PocketDimensionManager.isPocketDimension(world.provider.getDimension())) return;
        for (net.minecraft.entity.player.EntityPlayer player : world.playerEntities) {
            if (player instanceof EntityPlayerMP) syncPocket((EntityPlayerMP) player);
        }
    }

    /** Вопрос игроку (пока один вид: 1 = создать тотем смерти). a и b: числа для текста (цена телепорта, плата). */
    public static class PromptMessage implements IMessage {
        public int kind;
        public double a;
        public double b;
        public PromptMessage() {}
        public PromptMessage(int kind, double a, double b) { this.kind = kind; this.a = a; this.b = b; }
        @Override public void fromBytes(ByteBuf buf) { kind = buf.readInt(); a = buf.readDouble(); b = buf.readDouble(); }
        @Override public void toBytes(ByteBuf buf) { buf.writeInt(kind); buf.writeDouble(a); buf.writeDouble(b); }
    }

    public static void sendPrompt(EntityPlayerMP player, int kind, double a, double b) {
        CHANNEL_INSTANCE.sendTo(new PromptMessage(kind, a, b), player);
    }

    public static class SyncMessage implements IMessage {
        public NBTTagCompound data = new NBTTagCompound();
        public SyncMessage() {}
        public SyncMessage(NBTTagCompound data) { this.data = data == null ? new NBTTagCompound() : data; }
        @Override public void fromBytes(ByteBuf buf) { data = ByteBufUtils.readTag(buf); }
        @Override public void toBytes(ByteBuf buf) { ByteBufUtils.writeTag(buf, data); }
    }

    public static class PocketSyncMessage implements IMessage {
        public int dimension;
        public NBTTagCompound data = new NBTTagCompound();
        public PocketSyncMessage() {}
        public PocketSyncMessage(int dimension, NBTTagCompound data) {
            this.dimension = dimension;
            this.data = data == null ? new NBTTagCompound() : data;
        }
        @Override public void fromBytes(ByteBuf buf) {
            dimension = buf.readInt();
            data = ByteBufUtils.readTag(buf);
        }
        @Override public void toBytes(ByteBuf buf) {
            buf.writeInt(dimension);
            ByteBufUtils.writeTag(buf, data);
        }
    }

    public static class ActionMessage implements IMessage {
        public NetAction action;
        public int a, b;
        public double value;
        public String text = "";
        public ActionMessage() {}
        public ActionMessage(NetAction action, int a, int b, double value, String text) {
            this.action = action; this.a = a; this.b = b; this.value = value;
            this.text = text == null ? "" : text;
        }
        @Override public void fromBytes(ByteBuf buf) {
            action = NetAction.fromId(buf.readInt()); a = buf.readInt(); b = buf.readInt();
            value = buf.readDouble(); text = ByteBufUtils.readUTF8String(buf);
        }
        @Override public void toBytes(ByteBuf buf) {
            buf.writeInt(action == null ? 0 : action.id); buf.writeInt(a); buf.writeInt(b);
            buf.writeDouble(value); ByteBufUtils.writeUTF8String(buf, text);
        }
    }

    public static class ActionHandler implements IMessageHandler<ActionMessage, IMessage> {
        @Override public IMessage onMessage(ActionMessage message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> execute(player, message));
            return null;
        }
    }

    private static void execute(EntityPlayerMP p, ActionMessage m) {
        // Не допускаем NaN/Infinity в экономику и коэффициенты, а также лишние данные пакета.
        if (p == null || m == null || m.action == null
                || !Double.isFinite(m.value) || m.text == null || m.text.length() > 128) return;
        boolean shopAction = m.action == NetAction.TAKE_CREDIT
                || m.action == NetAction.TOGGLE_DECOY
                || m.action == NetAction.TOGGLE_SILENCE
                || m.action == NetAction.UPGRADE_BLOODSUCKER
                || m.action == NetAction.UPGRADE_ABILITY
                || m.action == NetAction.BUY_BODY_PERMANENT
                || m.action == NetAction.TOGGLE_BODY_RENT
                || m.action == NetAction.SPIN_LOOT;
        boolean totemAction = m.action == NetAction.CREATE_TOTEM
                || m.action == NetAction.MOVE_TOTEM
                || m.action == NetAction.DELETE_TOTEM
                || m.action == NetAction.TELEPORT_TOTEM
                || m.action == NetAction.TELEPORT_LAST_TOTEM
                || m.action == NetAction.DEATH_TOTEM_CHOICE;
        if (MorphIntegration.isMorphed(p) && !shopAction && !totemAction) return;
        switch (m.action) {
            case TAKE_CREDIT:
                double repay = Mana.takeCredit(p, m.value);
                if (repay > 0) p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.credit_taken",
                        (int) Math.ceil(repay), PocketConfig.creditDurationSec / 60), true);
                break;
            case TOGGLE_DECOY: Abilities.setDecoyEnabled(p, !Abilities.isDecoyEnabled(p)); break;
            case TOGGLE_SILENCE: Abilities.setSilenceEnabled(p, !Abilities.isSilenceEnabled(p)); break;
            case UPGRADE_BLOODSUCKER: AbilityStorage.tryUpgrade(p, AbilityType.BLOODSUCKER); break;
            case UPGRADE_ABILITY:
                if (m.a >= 0 && m.a < AbilityType.values().length)
                    AbilityStorage.tryUpgrade(p, AbilityType.values()[m.a]);
                break;
            case BUY_BODY_PERMANENT: if (m.a >= 0 && m.a < BodyEffects.IDS.length) BodyEffects.buyPerm(p, m.a); break;
            case TOGGLE_BODY_RENT: if (m.a >= 0 && m.a < BodyEffects.IDS.length) BodyEffects.toggleRent(p, m.a); break;
            case SPIN_LOOT: if (m.a >= 0 && m.a < 3) Loot.spin(p, m.a); break;
            case CREATE_TOTEM: Totems.create(p, m.text); break;
            case MOVE_TOTEM: Totems.move(p, m.a, m.b); break;
            case DELETE_TOTEM: Totems.delete(p, m.a); break;
            case TELEPORT_TOTEM: Totems.teleport(p, m.a); break;
            case TELEPORT_LAST_TOTEM:
                if (Totems.hasDeathTotem(p)) Totems.teleportDeath(p);
                else Totems.teleportLast(p);
                break;
            case TELEPORT_LOOK: Abilities.teleportLook(p); break;
            case TOGGLE_INVISIBILITY: Abilities.toggleInvisibility(p); break;
            case TOGGLE_SLOW: Abilities.toggleSlow(p); break;
            case TOGGLE_SPECTOR:
                if (Spector.isActive(p)) Spector.stop(p);
                else Spector.start(p);
                break;
            case PHASE_STEP: Spector.phaseStep(p); break;
            case SHOCKWAVE: Abilities.shockwave(p); break;
            case TOGGLE_THERMAL: Thermal.toggle(p); break;
            case TOGGLE_SHIELD: Shield.toggle(p); break;
            case START_FLIGHT: Flight.start(p); break;
            case SOFTFALL_TOGGLE: SoftFall.toggleByPlayer(p); break;
            case OPEN_WAREHOUSE:
                if (AbilityStorage.isUnlocked(p, AbilityType.WAREHOUSE)) p.openGui(PocketMod.instance, PocketMod.GUI_WAREHOUSE, p.world, 0, 0, 0);
                else p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
                break;
            case TOGGLE_ORE_SCANNER: OreScanner.toggle(p); break;
            case TOGGLE_POCKET: PocketTeleporter.toggle(p); break;
            case DASH: if (m.a >= 0 && m.a < 4) Dash.request(p, m.a); break;
            case DEATH_TOTEM_CHOICE: Totems.resolvePendingDeathTotem(p, m.a == 1); break;
            case TOGGLE_SCAN_TARGET: OreScanner.toggleTarget(p, m.a); break;
            case WAREHOUSE_CLICK: WarehouseSlots.click(p, m.a, m.b); break;
            case SET_SLOW_FACTOR:
                float factor = (float) Math.max(Abilities.minSlowFactor(p), Math.min(0.75, m.value));
                Abilities.setSlowFactor(p, factor);
                p.sendStatusMessage(new TextComponentTranslation(
                        factor <= 0.001f ? "pocketmod.msg.slow_stop" : "pocketmod.msg.slow_factor",
                        String.format("%.2f", factor)), true);
                break;
        }
        sync(p);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            sync(player);
            if (PocketDimensionManager.isPocketDimension(player.dimension)) syncPocket(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP && PocketDimensionManager.isPocketDimension(event.toDim)) {
            syncPocket((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof EntityPlayerMP
                && event.player.ticksExisted % 10 == 0) sync((EntityPlayerMP) event.player);
    }
}
