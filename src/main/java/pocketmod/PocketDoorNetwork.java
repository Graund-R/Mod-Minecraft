package pocketmod;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.relauncher.Side;

/** Client requests; all access and economy changes are validated on the server. */
public final class PocketDoorNetwork {
    private static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel("pocketdoor");
    private static final int REQUEST_STATE = 0;
    private static final int BUY_PAIR = 1;
    private static final int BUY_GOLD_KEY = 2;
    private static final int BUY_IRON_KEY = 3;
    private static final int TOGGLE_KEYS = 4;
    private static final int TOGGLE_GUEST_TEST = 5;
    private static final int TOGGLE_PAUSE = 6;
    private static final int KICK_ALL = 7;
    private static final int ADD_TRUSTED = 8;
    private static final int REMOVE_TRUSTED = 9;
    private static final int MORPH_TRANSFORM = 10;
    private static final int MORPH_DEMORPH = 11;

    private static volatile NBTTagCompound clientState = new NBTTagCompound();

    private PocketDoorNetwork() {}

    public static void registerServer() {
        CHANNEL.registerMessage(ActionHandler.class, ActionMessage.class, 0, Side.SERVER);
    }

    public static void registerClient() {
        CHANNEL.registerMessage(PocketDoorClientHandler.class, StateMessage.class, 1, Side.CLIENT);
    }

    public static void requestState() {
        send(REQUEST_STATE, 0, "", "");
    }

    public static void buyPair(String selectedPair) { send(BUY_PAIR, 0, selectedPair == null ? "" : selectedPair, ""); }
    public static void buyGoldKey(String pairId, boolean preserveOld) { send(BUY_GOLD_KEY, preserveOld ? 1 : 0, pairId, ""); }
    public static void buyIronKey(String pairId, boolean preserveOld) { send(BUY_IRON_KEY, preserveOld ? 1 : 0, pairId, ""); }
    public static void toggleKeys() { send(TOGGLE_KEYS, 0, "", ""); }
    public static void toggleGuestTest() { send(TOGGLE_GUEST_TEST, 0, "", ""); }
    public static void togglePause() { send(TOGGLE_PAUSE, 0, "", ""); }
    public static void kickAll() { send(KICK_ALL, 0, "", ""); }
    public static void addTrusted(String name) { send(ADD_TRUSTED, 0, "", name); }
    public static void removeTrusted(String name) { send(REMOVE_TRUSTED, 0, "", name); }
    public static void transform(int index) { send(MORPH_TRANSFORM, index, "", ""); }
    public static void demorph() { send(MORPH_DEMORPH, 0, "", ""); }

    private static void send(int action, int value, String pair, String text) {
        CHANNEL.sendToServer(new ActionMessage(action, value, pair, text));
    }

    public static NBTTagCompound getClientState() {
        return clientState.copy();
    }

    static void setClientState(NBTTagCompound state) {
        clientState = state == null ? new NBTTagCompound() : state.copy();
    }

    private static void sendState(EntityPlayerMP player) {
        PocketDoorData data = PocketDoorData.get(player.getServer().getWorld(0));
        UUID owner = player.getUniqueID();
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger("pairCount", data.pairCount(owner));
        state.setInteger("pairPrice", PocketConfig.doorPairPrice);
        state.setInteger("goldKeyPrice", PocketConfig.goldKeyPrice);
        state.setInteger("ironKeyPrice", PocketConfig.ironKeyPrice);
        state.setBoolean("morphismUnlocked", PocketConfig.morphEnabled
                && AbilityStorage.isUnlocked(player, AbilityType.MORPHISM));
        state.setBoolean("keysEnabled", data.keysEnabled(owner));
        state.setBoolean("accessPaused", data.accessPaused(owner));
        state.setBoolean("guestTest", data.guestTestEnabled(owner));

        NBTTagList pairs = new NBTTagList();
        for (String id : data.pairIds(owner)) {
            NBTTagCompound pair = new NBTTagCompound();
            pair.setString("id", id);
            pair.setInteger("doors", data.doorCount(id));
            pairs.appendTag(pair);
        }
        state.setTag("pairs", pairs);

        NBTTagList trusted = new NBTTagList();
        for (String name : data.trustedNames(owner)) {
            trusted.appendTag(new net.minecraft.nbt.NBTTagString(name));
        }
        state.setTag("trusted", trusted);
        CHANNEL.sendTo(new StateMessage(state), player);
    }

    public static final class ActionMessage implements IMessage {
        public int action;
        public int value;
        public String pair = "";
        public String text = "";

        public ActionMessage() {}
        public ActionMessage(int action, int value, String pair, String text) {
            this.action = action;
            this.value = value;
            this.pair = pair == null ? "" : pair;
            this.text = text == null ? "" : text;
        }
        @Override public void fromBytes(ByteBuf buf) {
            action = buf.readInt();
            value = buf.readInt();
            pair = ByteBufUtils.readUTF8String(buf);
            text = ByteBufUtils.readUTF8String(buf);
        }
        @Override public void toBytes(ByteBuf buf) {
            buf.writeInt(action);
            buf.writeInt(value);
            ByteBufUtils.writeUTF8String(buf, pair);
            ByteBufUtils.writeUTF8String(buf, text);
        }
    }

    public static final class StateMessage implements IMessage {
        public NBTTagCompound state = new NBTTagCompound();
        public StateMessage() {}
        public StateMessage(NBTTagCompound state) {
            this.state = state == null ? new NBTTagCompound() : state;
        }
        @Override public void fromBytes(ByteBuf buf) { state = ByteBufUtils.readTag(buf); }
        @Override public void toBytes(ByteBuf buf) { ByteBufUtils.writeTag(buf, state); }
    }

    public static final class ActionHandler implements IMessageHandler<ActionMessage, IMessage> {
        @Override public IMessage onMessage(final ActionMessage message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override public void run() { execute(player, message); }
            });
            return null;
        }
    }

    private static void execute(EntityPlayerMP player, ActionMessage message) {
        if (player == null || message == null || message.pair.length() > 64
                || message.text.length() > 16) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        PocketDoorData data = PocketDoorData.get(server.getWorld(0));
        UUID owner = player.getUniqueID();
        switch (message.action) {
            case REQUEST_STATE:
                sendState(player);
                return;
            case BUY_PAIR: {
                if (!AbilityStorage.isUnlocked(player, AbilityType.POCKET)) {
                    deny(player);
                    break;
                }
                String pairId = message.pair;
                boolean replacing = data.ownsPair(owner, pairId) && data.doorCount(pairId) < 2;
                int doorsToIssue = replacing ? 2 - data.doorCount(pairId) : 2;
                if (!replacing && !data.canBuyPair(owner)) {
                    player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.door_pair_limit"), true);
                    break;
                }
                double basePrice = PocketConfig.doorPairPrice * (doorsToIssue / 2.0D);
                double price = Mana.shopPrice(player, basePrice);
                if (!Mana.spend(player, price)) {
                    player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana",
                            (int) Math.ceil(price)), true);
                    break;
                }
                if (!replacing) pairId = data.createPair(owner);
                if (pairId == null) {
                    player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.door_pair_limit"), true);
                    break;
                }
                Mana.consumeDiscount(player);
                for (int i = 0; i < doorsToIssue; i++) {
                    give(player, PocketDoorItems.createPairItem(owner, pairId));
                }
                player.sendStatusMessage(new TextComponentTranslation(
                        replacing ? "pocketmod.msg.door_pair_replaced" : "pocketmod.msg.door_pair_bought"), true);
                break;
            }
            case BUY_GOLD_KEY:
            case BUY_IRON_KEY: {
                if (!AbilityStorage.isUnlocked(player, AbilityType.POCKET)) {
                    deny(player);
                    break;
                }
                if (!data.ownsPair(owner, message.pair)) {
                    player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.door_pair_missing"), true);
                    break;
                }
                boolean oneTime = message.action == BUY_IRON_KEY;
                double price = Mana.shopPrice(player, oneTime ? PocketConfig.ironKeyPrice : PocketConfig.goldKeyPrice);
                if (!Mana.spend(player, price)) {
                    player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.no_mana",
                            (int) Math.ceil(price)), true);
                    break;
                }
                Mana.consumeDiscount(player);
                if (message.value == 0) data.rotateKeyEpoch(owner);
                give(player, PocketKeys.createKeyFor(owner, message.pair, oneTime, data.keyEpoch(owner)));
                player.sendStatusMessage(new TextComponentTranslation(
                        oneTime ? "pocketmod.msg.temp_key_bought" : "pocketmod.msg.key_bought"), true);
                break;
            }
            case TOGGLE_KEYS:
                data.setKeysEnabled(owner, !data.keysEnabled(owner));
                player.sendStatusMessage(new TextComponentTranslation(
                        data.keysEnabled(owner) ? "pocketmod.msg.keys_enabled" : "pocketmod.msg.keys_disabled"), true);
                break;
            case TOGGLE_GUEST_TEST:
                data.setGuestTestEnabled(owner, !data.guestTestEnabled(owner));
                player.sendStatusMessage(new TextComponentTranslation(
                        data.guestTestEnabled(owner) ? "pocketmod.msg.guest_test_enabled"
                                : "pocketmod.msg.guest_test_disabled"), true);
                break;
            case TOGGLE_PAUSE:
                data.setAccessPaused(owner, !data.accessPaused(owner));
                player.sendStatusMessage(new TextComponentTranslation(
                        data.accessPaused(owner) ? "pocketmod.msg.access_paused" : "pocketmod.msg.access_resumed"), true);
                break;
            case KICK_ALL: {
                int dimension = PocketDimensionManager.getDimensionForOwner(server, owner);
                List<EntityPlayerMP> visitors = new ArrayList<EntityPlayerMP>(server.getPlayerList().getPlayers());
                for (EntityPlayerMP visitor : visitors) {
                    if (visitor.dimension == dimension && !visitor.getUniqueID().equals(owner)) {
                        PocketTeleporter.toggle(visitor);
                    }
                }
                player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.visitors_kicked"), true);
                break;
            }
            case ADD_TRUSTED:
            case REMOVE_TRUSTED: {
                String name = message.text.trim();
                if (!name.matches("[A-Za-z0-9_]{1,16}")) break;
                boolean add = message.action == ADD_TRUSTED;
                boolean changed = add ? data.addTrusted(owner, name) : data.removeTrusted(owner, name);
                player.sendStatusMessage(new TextComponentTranslation(
                        changed ? (add ? "pocketmod.msg.trusted_added" : "pocketmod.msg.trusted_removed")
                                : "pocketmod.msg.trusted_unchanged", name), true);
                break;
            }
            case MORPH_TRANSFORM:
                MorphIntegration.transform(player, message.value);
                break;
            case MORPH_DEMORPH:
                MorphIntegration.demorph(player);
                break;
            default:
                return;
        }
        sendState(player);
    }

    private static void deny(EntityPlayerMP player) {
        player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.need_shop"), true);
    }

    private static void give(EntityPlayerMP player, net.minecraft.item.ItemStack stack) {
        if (!player.inventory.addItemStackToInventory(stack)) player.dropItem(stack, false);
    }
}
