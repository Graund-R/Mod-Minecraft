package pocketmod;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.network.ByteBufUtils;

/**
 * Единая граница клиент/сервер.
 * Клиент отправляет только намерение и параметры; все игровые проверки и изменения
 * выполняются на серверном потоке. Клиент получает снимок постоянных данных игрока.
 */
public final class NetworkHandler {
    private static final String CHANNEL = "pocketmod";
    private static final int SYNC_ID = 0;
    private static final int ACTION_ID = 1;
    public static final SimpleNetworkWrapper CHANNEL_INSTANCE =
            NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);

    private NetworkHandler() {}

    public static void register() {
        CHANNEL_INSTANCE.registerMessage(SyncHandler.class, SyncMessage.class, SYNC_ID, Side.CLIENT);
        CHANNEL_INSTANCE.registerMessage(ActionHandler.class, ActionMessage.class, ACTION_ID, Side.SERVER);
        MinecraftForge.EVENT_BUS.register(NetworkHandler.class);
    }

    public static void action(int action, int a, int b, double value, String text) {
        CHANNEL_INSTANCE.sendToServer(new ActionMessage(action, a, b, value, text));
    }

    public static void sync(EntityPlayerMP player) {
        NBTTagCompound snapshot = PlayerData.root(player).copy();
        snapshot.setBoolean("pm_sync_silence_enabled", Abilities.isSilenceEnabled(player));
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
        public double value;
        public String text = "";
        public ActionMessage() {}
        public ActionMessage(int action, int a, int b, double value, String text) {
            this.action = action; this.a = a; this.b = b; this.value = value;
            this.text = text == null ? "" : text;
        }
        @Override public void fromBytes(ByteBuf buf) {
            action = buf.readInt(); a = buf.readInt(); b = buf.readInt();
            value = buf.readDouble(); text = ByteBufUtils.readUTF8String(buf);
        }
        @Override public void toBytes(ByteBuf buf) {
            buf.writeInt(action); buf.writeInt(a); buf.writeInt(b);
            buf.writeDouble(value); ByteBufUtils.writeUTF8String(buf, text);
        }
    }

    public static class SyncHandler implements IMessageHandler<SyncMessage, IMessage> {
        @Override public IMessage onMessage(SyncMessage message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                net.minecraft.entity.player.EntityPlayer player =
                        net.minecraft.client.Minecraft.getMinecraft().player;
                if (player == null || message.data == null) return;
                PlayerData.replace(player, message.data);
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
            case 1:\n                double repay = Mana.takeCredit(p, m.value);\n                if (repay > 0) p.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.credit_taken",\n                        (int) Math.ceil(repay), PocketConfig.creditDurationSec / 60), true);\n                break;
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
                && event.player.ticksExisted % 10 == 0) {
            sync((EntityPlayerMP) event.player);
        }
    }
}
