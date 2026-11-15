package pocketmod;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
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
        CHANNEL_INSTANCE.registerMessage(SyncHandler.class, SyncMessage.class, 0, Side.CLIENT);
        CHANNEL_INSTANCE.registerMessage(ActionHandler.class, ActionMessage.class, 1, Side.SERVER);
        MinecraftForge.EVENT_BUS.register(NetworkHandler.class);
    }

    public static void action(int action, int a, int b, double value, String text) {
        CHANNEL_INSTANCE.sendToServer(new ActionMessage(action, a, b, value, text));
    }

    public static void actionPosition(int action, double x, double y, double z, float yaw, float pitch) {
        ActionMessage message = new ActionMessage(action, 0, 0, 0, "");
        message.x = x; message.y = y; message.z = z;
        message.yaw = yaw; message.pitch = pitch;
        CHANNEL_INSTANCE.sendToServer(message);
    }

    public static void sync(EntityPlayerMP player) {
        NBTTagCompound snapshot = PlayerData.root(player).copy();
        snapshot.setBoolean("pm_sync_slow", player.getEntityData().getBoolean("pocketmod_slow"));
        snapshot.setBoolean("pm_sync_invis", player.getEntityData().getBoolean("pocketmod_invis"));
        snapshot.setBoolean("pm_sync_thermal", player.getEntityData().getBoolean("pm_thermal"));
        snapshot.setBoolean("pm_sync_spector", player.getEntityData().getBoolean("pm_spec"));
        snapshot.setBoolean("pm_sync_softfall", player.getEntityData().getBoolean("pm_softfall"));
        snapshot.setBoolean("pm_sync_flight", player.getEntityData().getBoolean("pm_flight"));
        snapshot.setBoolean("pm_sync_shield", player.getEntityData().getBoolean("pm_shield"));
        snapshot.setFloat("pm_sync_slow_factor", player.getEntityData().getFloat("pocketmod_slow_factor"));
        CHANNEL_INSTANCE.sendTo(new SyncMessage(snapshot), player);
    }

    public static class SyncMessage implements IMessage {
        private NBTTagCompound data = new NBTTagCompound();
        public SyncMessage() {}
        public SyncMessage(NBTTagCompound data) { this.data = data == null ? new NBTTagCompound() : data; }
        @Override public void fromBytes(ByteBuf buf) { data = ByteBufUtils.readTag(buf); }
        @Override public void toBytes(ByteBuf buf) { ByteBufUtils.writeTag(buf, data); }
    }

    public static class ActionMessage implements IMessage {
        public int action, a, b;
        public double value, x, y, z;
        public float yaw, pitch;
        public String text = "";
        public ActionMessage() {}
        public ActionMessage(int action, int a, int b, double value, String text) {
            this.action = action; this.a = a; this.b = b; this.value = value;
            this.text = text == null ? "" : text;
        }
        @Override public void fromBytes(ByteBuf buf) {
            action = buf.readInt(); a = buf.readInt(); b = buf.readInt();
            value = buf.readDouble(); text = ByteBufUtils.readUTF8String(buf);
            x = buf.readDouble(); y = buf.readDouble(); z = buf.readDouble();
            yaw = buf.readFloat(); pitch = buf.readFloat();
        }
        @Override public void toBytes(ByteBuf buf) {
            buf.writeInt(action); buf.writeInt(a); buf.writeInt(b);
            buf.writeDouble(value); ByteBufUtils.writeUTF8String(buf, text);
            buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
            buf.writeFloat(yaw); buf.writeFloat(pitch);
        }
    }

    public static class SyncHandler implements IMessageHandler<SyncMessage, IMessage> {
        @Override public IMessage onMessage(SyncMessage message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                net.minecraft.entity.player.EntityPlayer player =
                        net.minecraft.client.Minecraft.getMinecraft().player;
                if (player != null && message.data != null) PlayerData.replace(player, message.data);
            });
            return null;
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
        switch (m.action) {
            case 1:
                double repay = Mana.takeCredit(p, m.value);
                if (repay > 0) p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.credit_taken",
                        (int) Math.ceil(repay), PocketConfig.creditDurationSec / 60), true);
                break;
            case 2: Abilities.setDecoyEnabled(p, !Abilities.isDecoyEnabled(p)); break;
            case 3: Abilities.setSilenceEnabled(p, !Abilities.isSilenceEnabled(p)); break;
            case 4: AbilityStorage.tryUpgrade(p, AbilityType.BLOODSUCKER); break;
            case 5:
                if (m.a >= 0 && m.a < AbilityType.values().length)
                    AbilityStorage.tryUpgrade(p, AbilityType.values()[m.a]);
                break;
            case 6: if (m.a >= 0 && m.a < 5) BodyEffects.buyPerm(p, m.a); break;
            case 7: if (m.a >= 0 && m.a < 5) BodyEffects.toggleRent(p, m.a); break;
            case 8: if (m.a >= 0 && m.a < 3) Loot.spin(p, m.a); break;
            case 9: Totems.create(p, m.text); break;
            case 10: Totems.move(p, m.a, m.b); break;
            case 11: Totems.delete(p, m.a); break;
            case 12: Totems.teleport(p, m.a); break;
            case 13:
                if (Totems.hasDeathTotem(p)) Totems.teleportDeath(p);
                else Totems.teleportLast(p);
                break;
            case 14: Abilities.teleportLook(p); break;
            case 15: Abilities.toggleInvisibility(p); break;
            case 16: Abilities.toggleSlow(p); break;
            case 17:
                if (Spector.isActive(p)) Spector.stop(p);
                else Spector.start(p);
                break;
            case 18: Spector.phaseStep(p, m.x, m.y, m.z, m.yaw, m.pitch); break;
            case 19: Abilities.shockwave(p); break;
            case 20: Thermal.toggle(p); break;
            case 21: Shield.toggle(p); break;
            case 22: Flight.start(p); break;
            case 23: PocketTeleporter.toggle(p); break;
            case 24:
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
        if (event.player instanceof EntityPlayerMP) sync((EntityPlayerMP) event.player);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof EntityPlayerMP
                && event.player.ticksExisted % 10 == 0) sync((EntityPlayerMP) event.player);
    }
}
