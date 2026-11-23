package pocketmod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Открытие ветви отделено от её покупки. Проверки вызываются и из UI, и сервером. */
@Mod.EventBusSubscriber(modid = PocketMod.MODID)
public final class ExileProgression {
    private static final UUID WEAKNESS = UUID.fromString("94702537-3663-48d2-a945-2cd37541cc51");

    private ExileProgression() {}

    public static boolean inMode(EntityPlayer p) {
        return p.world != null
                && (p.world.isRemote
                        ? ExileMode.state(p).getBoolean("enabled")
                        : ExileMode.enabled(p.world));
    }

    public static boolean available(EntityPlayer p, String id) {
        return !inMode(p) || ExileMode.state(p).getBoolean("open_" + id);
    }

    public static boolean require(EntityPlayer p, String id) {
        if (available(p, id)) return true;
        p.sendStatusMessage(new TextComponentTranslation("pocketmod.exile.need_discovery"), true);
        return false;
    }

    public static List<String> unopened(EntityPlayer p) {
        List<String> choices = new ArrayList<>();
        for (AbilityType t : AbilityType.values()) if (!available(p, t.id)) choices.add(t.id);
        for (String id : BodyEffects.IDS)
            if (!available(p, "body_" + id)) choices.add("body_" + id);
        return choices;
    }

    public static void discover(EntityPlayerMP p) {
        List<String> choices = unopened(p);
        if (choices.isEmpty()) {
            Mana.add(p, 100);
            return;
        }
        String id = choices.get(p.getRNG().nextInt(choices.size()));
        ExileMode.state(p).setBoolean("open_" + id, true);
        ExileMode.progress(p, "discovery_" + id, 2);
        String title =
                id.startsWith("body_")
                        ? "pocketmod.body." + id.substring(5)
                        : "pocketmod.ability." + id;
        p.sendMessage(
                new TextComponentTranslation(
                        "pocketmod.exile.discovered", new TextComponentTranslation(title)));
        NetworkHandler.sync(p);
    }

    public static int config(EntityPlayer p, String key, int fallback) {
        NBTTagCompound state = ExileMode.state(p);
        return p.world != null && p.world.isRemote && state.hasKey("config_" + key)
                ? state.getInteger("config_" + key)
                : fallback;
    }

    public static int slots(EntityPlayer p) {
        if (!inMode(p)
                || ExileMode.state(p).getBoolean("cured")
                || p.isCreative()
                || (p.isSpectator() && !Spector.isActive(p))) return 36;
        return Math.min(
                36,
                config(p, "slots", ExileConfig.initialSlots)
                        + ExileMode.state(p).getInteger("inventory_level") * 9);
    }

    public static int basePrice(EntityPlayer p, int kind) {
        int level = ExileMode.state(p).getInteger(kind == 0 ? "inventory_level" : "weakness_level");
        if (kind == 0 && slots(p) >= 36 || kind == 1 && level >= 2) return -1;
        return config(p, "base_price", ExileConfig.basePrice) * (level + 1);
    }

    public static void buyBase(EntityPlayerMP p, int kind) {
        if (!inMode(p) || kind < 0 || kind > 1) return;
        int price = basePrice(p, kind);
        if (price < 0) return;
        double amount = Mana.shopPrice(p, price);
        if (!Mana.spend(p, amount)) {
            p.sendStatusMessage(
                    new TextComponentTranslation("pocketmod.msg.no_mana", (int) Math.ceil(amount)),
                    true);
            return;
        }
        Mana.consumeDiscount(p);
        String key = kind == 0 ? "inventory_level" : "weakness_level";
        ExileMode.state(p).setInteger(key, ExileMode.state(p).getInteger(key) + 1);
        ExileMode.progress(p, "base_" + kind + "_" + ExileMode.state(p).getInteger(key), 1);
        enforce(p);
    }

    public static void enforce(EntityPlayerMP p) {
        if (!inMode(p)) return;
        int limit = slots(p);
        // Содержимое закрытого слота перемещается в доступные, остаток безопасно выпадает.
        for (int i = limit; i < 36; i++) {
            ItemStack stack = p.inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            p.inventory.setInventorySlotContents(i, ItemStack.EMPTY);
            insert(p, stack, limit);
            if (!stack.isEmpty()) p.dropItem(stack, false);
            p.inventory.markDirty();
        }
        IAttributeInstance attribute = p.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        boolean weak =
                ExileMode.active(p)
                        && !p.isCreative()
                        && !(p.isSpectator() && !Spector.isActive(p));
        double amount =
                weak ? -0.25 * Math.max(0, 2 - ExileMode.state(p).getInteger("weakness_level")) : 0;
        AttributeModifier old = attribute.getModifier(WEAKNESS);
        if (old != null && old.getAmount() == amount) return;
        if (old != null) attribute.removeModifier(old);
        if (amount != 0)
            attribute.applyModifier(
                    new AttributeModifier(WEAKNESS, "exile_initial_weakness", amount, 2));
    }

    private static void insert(EntityPlayer p, ItemStack stack, int limit) {
        for (int i = 0; i < limit && !stack.isEmpty(); i++) {
            ItemStack target = p.inventory.getStackInSlot(i);
            if (target.isEmpty()
                    || !ItemStack.areItemsEqual(target, stack)
                    || !ItemStack.areItemStackTagsEqual(target, stack)) continue;
            int move = Math.min(stack.getCount(), target.getMaxStackSize() - target.getCount());
            target.grow(move);
            stack.shrink(move);
        }
        for (int i = 0; i < limit && !stack.isEmpty(); i++)
            if (p.inventory.getStackInSlot(i).isEmpty()) {
                ItemStack placed =
                        stack.splitStack(Math.min(stack.getCount(), stack.getMaxStackSize()));
                p.inventory.setInventorySlotContents(i, placed);
            }
    }

    @SubscribeEvent
    public static void pickup(EntityItemPickupEvent e) {
        EntityPlayer p = e.getEntityPlayer();
        if (!inMode(p)) return;
        ItemStack stack = e.getItem().getItem();
        int room = 0;
        for (int i = 0; i < slots(p); i++) {
            ItemStack target = p.inventory.getStackInSlot(i);
            if (target.isEmpty()) room += stack.getMaxStackSize();
            else if (ItemStack.areItemsEqual(stack, target)
                    && ItemStack.areItemStackTagsEqual(stack, target))
                room += Math.max(0, target.getMaxStackSize() - target.getCount());
        }
        if (room == 0) e.setCanceled(true);
        else if (room < stack.getCount()) {
            ItemStack taken = stack.splitStack(room);
            insert(p, taken, slots(p));
            p.onItemPickup(e.getItem(), room);
            p.inventory.markDirty();
            e.setCanceled(true);
        }
    }
}
