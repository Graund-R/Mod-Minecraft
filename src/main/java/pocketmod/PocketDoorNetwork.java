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
    private static volatile boolean morphCatalogPending = false;
    private static volatile boolean totemGuiPending = false;

    private PocketDoorNetwork() {}

    public static void registerServer() {
        CHANNEL.registerMessage(ActionHandler.class, ActionMessage.class, 0, Side.SERVER);
        // The common relay keeps the discriminator table identical on dedicated servers
        // and clients, without loading any net.minecraft.client classes server-side.
        CHANNEL.registerMessage(StateRelayHandler.class, StateMessage.class, 1, Side.CLIENT);
    }

    public static void requestState() {
        send(REQUEST_STATE, 0, "", "");
    }

    public static void requestMorphCatalog() {
        morphCatalogPending = true;
        requestState();
    }

    static boolean consumeMorphCatalogPending() {
        boolean pending = morphCatalogPending;
        morphCatalogPending = false;
        return pending;
    }

    public static void upgradeAbility(AbilityType type) {
        if (type != null) send(12, type.ordinal(), "", "");
    }

    public static void buyBodyEffect(int index) { send(13, index, "", ""); }
    public static void toggleBodyRent(int index) { send(14, index, "", ""); }
    public static void toggleDecoy() { send(15, 0, "", ""); }
    public static void toggleSilence() { send(16, 0, "", ""); }
    public static void takeCredit(int amount) { send(17, amount, "", ""); }
    public static void spinLoot(int tier) { send(18, tier, "", ""); }
    public static void togglePocket() { send(19, 0, "", ""); }
    public static void teleportLook() { send(20, 0, "", ""); }
    public static void toggleInvisibility() { send(21, 0, "", ""); }
    public static void toggleSlow() { send(22, 0, "", ""); }
    public static void toggleSpector() { send(23, 0, "", ""); }
    public static void phaseStep(double x, double y, double z, float yaw, float pitch) {
        CHANNEL.sendToServer(new ActionMessage(24, 0, "", "", x, y, z, yaw, pitch));
    }
    public static void toggleThermal() { send(25, 0, "", ""); }
    public static void toggleShield() { send(26, 0, "", ""); }
    public static void shockwave() { send(27, 0, "", ""); }
    public static void startFlight() { send(28, 0, "", ""); }
    public static void setSlowFactor(float factor) { send(29, 0, "", "", factor, 0, 0, 0, 0); }
    public static void teleportLastTotem() { send(30, 0, "", ""); }
    public static void teleportDeathTotem() { send(31, 0, "", ""); }
    public static void openTotemGui() { totemGuiPending = true; requestState(); }

    static boolean consumeTotemGuiPending() {
        boolean pending = totemGuiPending;
        totemGuiPending = false;
        return pending;
    }
    public static void createTotem(String name) { send(32, 0, "", name); }
    public static void teleportTotem(int index) { send(33, index, "", ""); }
    public static void moveTotem(int index, int delta) { send(34, index, "", "", delta, 0, 0, 0, 0); }
    public static void deleteTotem(int index) { send(35, index, "", ""); }

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

    private static void send(int action, int value, String pair, String text,
            double x, double y, double z, float yaw, float pitch) {
        CHANNEL.sendToServer(new ActionMessage(action, value, pair, text, x, y, z, yaw, pitch));
    }

    public static NBTTagCompound getClientState() {
        return clientState.copy();
    }

    static void setClientState(NBTTagCompound state) {
        clientState = state == null ? new NBTTagCompound() : state.copy();
    }

    public static void syncState(EntityPlayerMP player) {
        if (player != null) sendState(player);
    }

    private static void sendState(EntityPlayerMP player) {
        PocketDoorData data = PocketDoorData.get(player.getServer().getWorld(0));
        UUID owner = player.getUniqueID();
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger("pairCount", data.pairCount(owner));
        state.setBoolean("ready", true);
        state.setInteger("pairPrice", PocketConfig.doorPairPrice);
        state.setInteger("goldKeyPrice", PocketConfig.goldKeyPrice);
        state.setInteger("ironKeyPrice", PocketConfig.ironKeyPrice);
        state.setBoolean("morphismUnlocked", PocketConfig.morphEnabled
                && AbilityStorage.isUnlocked(player, AbilityType.MORPHISM));
        state.setBoolean("totemUnlocked", AbilityStorage.isUnlocked(player, AbilityType.TOTEM));
        state.setBoolean("morphismAvailable", PocketConfig.morphEnabled && MorphIntegration.isAvailable());
        state.setInteger("lootPity", Loot.pity(player));
        state.setBoolean("keysEnabled", data.keysEnabled(owner));
        state.setBoolean("accessPaused", data.accessPaused(owner));
        state.setBoolean("guestTest", data.guestTestEnabled(owner));
        state.setBoolean("spectorActive", Spector.isActive(player));
        state.setBoolean("slowEnabled", Abilities.isSlow(player));
        state.setFloat("slowFactor", Abilities.getSlowFactor(player));
        state.setFloat("slowMinFactor", Abilities.minSlowFactor(player));
        state.setBoolean("thermalEnabled", Thermal.isOn(player));
        state.setBoolean("shieldEnabled", Shield.isOn(player));
        state.setBoolean("flightEnabled", Flight.isArmed(player));
        state.setBoolean("softFallEnabled", SoftFall.isActive(player));
        state.setInteger("thermalRadius", Thermal.radius(player));
        state.setBoolean("invisibilityEnabled", Abilities.isInvisible(player));
        state.setBoolean("hasDeathTotem", Totems.hasDeathTotem(player));
        state.setInteger("totemSlots", Totems.maxSlots(player));
        NBTTagList totems = new NBTTagList();
        for (Totems.Totem saved : Totems.get(player)) {
            NBTTagCompound totem = new NBTTagCompound();
            totem.setString("name", saved.name);
            totem.setInteger("dim", saved.dim);
            totem.setDouble("x", saved.x);
            totem.setDouble("y", saved.y);
            totem.setDouble("z", saved.z);
            totems.appendTag(totem);
        }
        state.setTag("totems", totems);
        state.setDouble("mana", Mana.get(player));
        state.setDouble("priceMultiplier", Mana.getPriceMultiplier(player));
        state.setDouble("discount", Mana.getDiscount(player));
        state.setDouble("credit", Mana.getCredit(player));
        state.setLong("creditLeftMs", Mana.getCreditLeftMs(player));
        state.setBoolean("hasCredit", Mana.hasCredit(player));
        state.setBoolean("decoyEnabled", Abilities.isDecoyEnabled(player));
        state.setBoolean("silenceEnabled", Abilities.isSilenceEnabled(player));
        state.setString("lastLoot", Loot.last(player));
        state.setDouble("totalDrain", MorphIntegration.isMorphed(player) ? 0.0D
                : Abilities.totalDrainPerSec(player));
        double barScale = Math.max(1, PocketConfig.hudBarScale);
        for (AbilityType type : AbilityType.values()) {
            int level = AbilityStorage.getLevel(player, type);
            if (level > 0 && level <= type.levelCosts.length) {
                barScale = Math.max(barScale, type.levelCosts[level - 1]);
            }
        }
        state.setDouble("barScale", barScale);
        int[] abilityLevels = new int[AbilityType.values().length];
        for (AbilityType type : AbilityType.values()) {
            abilityLevels[type.ordinal()] = AbilityStorage.getLevel(player, type);
        }
        state.setIntArray("abilityLevels", abilityLevels);
        int[] bodyLevels = new int[BodyEffects.IDS.length];
        boolean[] bodyRent = new boolean[BodyEffects.IDS.length];
        for (int i = 0; i < BodyEffects.IDS.length; i++) {
            bodyLevels[i] = BodyEffects.perm(player, i);
            bodyRent[i] = BodyEffects.rent(player, i);
        }
        state.setIntArray("bodyLevels", bodyLevels);
        state.setIntArray("bodyRent", new int[] { bodyRent[0] ? 1 : 0, bodyRent[1] ? 1 : 0,
                bodyRent[2] ? 1 : 0, bodyRent[3] ? 1 : 0, bodyRent[4] ? 1 : 0 });

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
        public double x, y, z;
        public float yaw, pitch;

        public ActionMessage() {}
        public ActionMessage(int action, int value, String pair, String text) {
            this(action, value, pair, text, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F);
        }
        public ActionMessage(int action, int value, String pair, String text,
                double x, double y, double z, float yaw, float pitch) {
            this.action = action;
            this.value = value;
            this.pair = pair == null ? "" : pair;
            this.text = text == null ? "" : text;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
        @Override public void fromBytes(ByteBuf buf) {
            action = buf.readInt();
            value = buf.readInt();
            pair = ByteBufUtils.readUTF8String(buf);
            text = ByteBufUtils.readUTF8String(buf);
            x = buf.readDouble();
            y = buf.readDouble();
            z = buf.readDouble();
            yaw = buf.readFloat();
            pitch = buf.readFloat();
        }
        @Override public void toBytes(ByteBuf buf) {
            buf.writeInt(action);
            buf.writeInt(value);
            ByteBufUtils.writeUTF8String(buf, pair);
            ByteBufUtils.writeUTF8String(buf, text);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeFloat(yaw);
            buf.writeFloat(pitch);
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

    public static final class StateRelayHandler implements IMessageHandler<StateMessage, IMessage> {
        @Override
        public IMessage onMessage(final StateMessage message, MessageContext context) {
            try {
                Class<?> handler = Class.forName("pocketmod.PocketDoorClientHandler");
                handler.getMethod("receive", NBTTagCompound.class).invoke(null, message.state.copy());
            } catch (Throwable error) {
                System.err.println("[PocketMod] Could not relay client state: " + error);
            }
            return null;
        }
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
                || message.text.length() > 24) return;
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
                if (!AbilityStorage.isUnlocked(player, AbilityType.POCKET)) {
                    deny(player);
                    break;
                }
                data.setKeysEnabled(owner, !data.keysEnabled(owner));
                player.sendStatusMessage(new TextComponentTranslation(
                        data.keysEnabled(owner) ? "pocketmod.msg.keys_enabled" : "pocketmod.msg.keys_disabled"), true);
                break;
            case TOGGLE_GUEST_TEST:
                if (!AbilityStorage.isUnlocked(player, AbilityType.POCKET)) {
                    deny(player);
                    break;
                }
                data.setGuestTestEnabled(owner, !data.guestTestEnabled(owner));
                player.sendStatusMessage(new TextComponentTranslation(
                        data.guestTestEnabled(owner) ? "pocketmod.msg.guest_test_enabled"
                                : "pocketmod.msg.guest_test_disabled"), true);
                break;
            case TOGGLE_PAUSE:
                if (!AbilityStorage.isUnlocked(player, AbilityType.POCKET)) {
                    deny(player);
                    break;
                }
                data.setAccessPaused(owner, !data.accessPaused(owner));
                player.sendStatusMessage(new TextComponentTranslation(
                        data.accessPaused(owner) ? "pocketmod.msg.access_paused" : "pocketmod.msg.access_resumed"), true);
                break;
            case KICK_ALL: {
                if (!AbilityStorage.isUnlocked(player, AbilityType.POCKET)) {
                    deny(player);
                    break;
                }
                // Одно действие: сначала закрываем вход для новых гостей, затем удаляем уже вошедших.
                data.setAccessPaused(owner, true);
                int dimension = PocketDimensionManager.getDimensionForOwner(server, owner);
                List<EntityPlayerMP> visitors = new ArrayList<EntityPlayerMP>(server.getPlayerList().getPlayers());
                for (EntityPlayerMP visitor : visitors) {
                    if (visitor.dimension == dimension && !visitor.getUniqueID().equals(owner)) {
                        PocketTeleporter.toggle(visitor);
                    }
                }
                player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.visitors_kicked_paused"), true);
                break;
            }
            case ADD_TRUSTED:
            case REMOVE_TRUSTED: {
                if (!AbilityStorage.isUnlocked(player, AbilityType.POCKET)) {
                    deny(player);
                    break;
                }
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
            case 12:
                if (message.value >= 0 && message.value < AbilityType.values().length) {
                    AbilityStorage.tryUpgrade(player, AbilityType.values()[message.value]);
                }
                break;
            case 13:
                if (message.value >= 0 && message.value < BodyEffects.IDS.length) {
                    BodyEffects.buyPerm(player, message.value);
                }
                break;
            case 14:
                if (message.value >= 0 && message.value < BodyEffects.IDS.length) {
                    BodyEffects.toggleRent(player, message.value);
                }
                break;
            case 15:
                Abilities.setDecoyEnabled(player, !Abilities.isDecoyEnabled(player));
                break;
            case 16:
                Abilities.setSilenceEnabled(player, !Abilities.isSilenceEnabled(player));
                break;
            case 17:
                if (message.value >= PocketConfig.creditMin && message.value <= PocketConfig.creditMax
                        && !Mana.hasCredit(player)) {
                    double repay = Mana.takeCredit(player, message.value);
                    if (repay > 0) player.sendStatusMessage(new TextComponentTranslation(
                            "pocketmod.msg.credit_taken", (int) Math.ceil(repay),
                            PocketConfig.creditDurationSec / 60), true);
                }
                break;
            case 18:
                if (message.value >= 0 && message.value < PocketConfig.lootStakes.length) {
                    Loot.spin(player, message.value);
                }
                break;
            case 19:
                PocketTeleporter.toggle(player);
                break;
            case 20:
                Abilities.teleportLook(player);
                break;
            case 21:
                Abilities.toggleInvisibility(player);
                break;
            case 22:
                Abilities.toggleSlow(player);
                break;
            case 23:
                if (Spector.isActive(player)) Spector.stop(player);
                else Spector.start(player);
                break;
            case 24:
                if (Double.isNaN(message.x) || Double.isInfinite(message.x)
                        || Double.isNaN(message.y) || Double.isInfinite(message.y)
                        || Double.isNaN(message.z) || Double.isInfinite(message.z)
                        || Float.isNaN(message.yaw) || Float.isInfinite(message.yaw)
                        || Float.isNaN(message.pitch) || Float.isInfinite(message.pitch)
                        || Math.abs(message.x - player.posX) > 64
                        || Math.abs(message.y - player.posY) > 64
                        || Math.abs(message.z - player.posZ) > 64) return;
                float yaw = message.yaw % 360.0F;
                float pitch = Math.max(-90.0F, Math.min(90.0F, message.pitch));
                Spector.phaseStep(player, message.x, message.y, message.z, yaw, pitch);
                break;
            case 25:
                Thermal.toggle(player);
                break;
            case 26:
                Shield.toggle(player);
                break;
            case 27:
                Abilities.shockwave(player);
                break;
            case 28:
                Flight.start(player);
                break;
            case 29: {
                if (!Abilities.isSlow(player) || MorphIntegration.isMorphed(player)) break;
                float min = Abilities.minSlowFactor(player);
                float factor = (float) message.x;
                if (Float.isNaN(factor) || Float.isInfinite(factor)) break;
                factor = Math.max(min, Math.min(0.75F, factor));
                Abilities.setSlowFactor(player, factor);
                player.sendStatusMessage(new TextComponentTranslation("pocketmod.msg.slow_factor",
                        String.format("%.2f", factor)), true);
                break;
            }
            case 30:
                if (!AbilityStorage.isUnlocked(player, AbilityType.TOTEM)) {
                    deny(player);
                    break;
                }
                if (Totems.hasDeathTotem(player)) Totems.teleportDeath(player);
                else Totems.teleportLast(player);
                break;
            case 31:
                if (!AbilityStorage.isUnlocked(player, AbilityType.TOTEM)) {
                    deny(player);
                    break;
                }
                Totems.teleportDeath(player);
                break;
            case 32:
                if (!AbilityStorage.isUnlocked(player, AbilityType.TOTEM)) {
                    deny(player);
                    break;
                }
                Totems.create(player, message.text);
                break;
            case 33:
                if (!AbilityStorage.isUnlocked(player, AbilityType.TOTEM)) {
                    deny(player);
                    break;
                }
                Totems.teleport(player, message.value);
                break;
            case 34:
                if (!AbilityStorage.isUnlocked(player, AbilityType.TOTEM)) {
                    deny(player);
                    break;
                }
                Totems.move(player, message.value, (int) message.x);
                break;
            case 35:
                if (!AbilityStorage.isUnlocked(player, AbilityType.TOTEM)) {
                    deny(player);
                    break;
                }
                Totems.delete(player, message.value);
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
