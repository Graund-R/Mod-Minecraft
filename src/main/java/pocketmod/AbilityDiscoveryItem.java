package pocketmod;

import net.minecraft.advancements.Advancement;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

/** Поднятый свиток хранит открытие до активации. Порядок метаданных сохраняется между версиями. */
public final class AbilityDiscoveryItem extends Item {
    public static final String[] IDS = {
        "pocket",
        "teleport",
        "invis",
        "slow",
        "decoy",
        "spector",
        "thermal",
        "shield",
        "flight",
        "totem",
        "softfall",
        "bloodsucker",
        "magnet",
        "wave",
        "scanner",
        "warehouse",
        "dash",
        "morphism",
        "body_hearts",
        "body_speed",
        "body_strength",
        "body_fire",
        "body_breath",
        "body_regen"
    };

    public AbilityDiscoveryItem() {
        setRegistryName(PocketMod.MODID, "ability_discovery");
        setUnlocalizedName("pocketmod.ability_discovery");
        setHasSubtypes(true);
        setMaxStackSize(1);
        setCreativeTab(PocketCreativeTab.ITEMS);
    }

    public static String id(ItemStack stack) {
        int meta = stack.getMetadata();
        return stack.getItem() == ExileItems.DISCOVERY && meta >= 0 && meta < IDS.length
                ? IDS[meta]
                : "";
    }

    public static ItemStack create(String id) {
        for (int i = 0; i < IDS.length; i++)
            if (IDS[i].equals(id)) return new ItemStack(ExileItems.DISCOVERY, 1, i);
        return ItemStack.EMPTY;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        String id = id(stack);
        return id.isEmpty() ? super.getUnlocalizedName(stack) : "item.pocketmod.discovery_" + id;
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;
        for (int i = 0; i < IDS.length; i++) items.add(new ItemStack(this, 1, i));
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(
            World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        RayTraceResult hit = player.rayTrace(5, 1);
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK)
            return new ActionResult<>(EnumActionResult.PASS, stack);
        String id = id(stack);
        if (id.isEmpty() || !ExileProgression.inMode(player) || !ExileProgression.allowedInExile(id))
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        if (world.isRemote) return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        if (ExileProgression.available(player, id)) {
            player.sendStatusMessage(
                    new TextComponentTranslation("pocketmod.exile.discovery_known"), true);
            return new ActionResult<>(EnumActionResult.SUCCESS, consume(stack));
        }
        ExileMode.state(player).setBoolean("open_" + id, true);
        if (id.equals(ExileMode.state(player).getString("pending_discovery")))
            ExileMode.state(player).removeTag("pending_discovery");
        ExileMode.progress(player, "discovery_" + id, 2);
        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        grant(serverPlayer, "root", "entered");
        grant(serverPlayer, "discover_" + id, "activated");
        ItemStack remaining = consume(stack);
        String title =
                id.startsWith("body_")
                        ? "pocketmod.body." + id.substring(5)
                        : "pocketmod.ability." + id;
        player.sendMessage(
                new TextComponentTranslation(
                        "pocketmod.exile.discovered", new TextComponentTranslation(title)));
        NetworkHandler.sync(serverPlayer);
        // Returning the exhausted original stack lets vanilla creative mode restore its count.
        // EMPTY stays empty through PlayerInteractionManager's creative restoration.
        return new ActionResult<>(EnumActionResult.SUCCESS, remaining);
    }

    static ItemStack consume(ItemStack stack) {
        stack.shrink(1);
        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    private static void grant(EntityPlayerMP player, String id, String criterion) {
        Advancement advancement =
                player.getServerWorld()
                        .getAdvancementManager()
                        .getAdvancement(new ResourceLocation(PocketMod.MODID, "exile/" + id));
        if (advancement != null) player.getAdvancements().grantCriterion(advancement, criterion);
    }
}
