package pocketmod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.OreDictionary;
import org.lwjgl.opengl.GL11;

/**
 * Клиентская часть сканера руд (режим тепловизора, Shift+C).
 * Поиск идёт по загруженным чанкам кусками (по паре чанков за тик), поэтому не даёт скачков кадра;
 * состояние блока -> «какая это руда» кэшируется. Цели можно включать и выключать (Ctrl+C), на экране
 * показывается сводка: что найдено, сколько и насколько близко.
 */
@SideOnly(Side.CLIENT)
public final class OreScannerClient {

    private static final class Hit {
        final BlockPos pos;
        final int idx;
        final double d2;

        Hit(BlockPos pos, int idx, double d2) {
            this.pos = pos;
            this.idx = idx;
            this.d2 = d2;
        }
    }

    private static final int MAX_TARGETS = 31;
    private static final int MAX_MARKS = 600;
    private static final int CHUNKS_PER_TICK = 2;
    private static final int PERIOD_TICKS = 40;

    private static List<Hit> ores = new ArrayList<Hit>();
    private static final List<Hit> pending = new ArrayList<Hit>();
    private static final List<int[]> queue = new ArrayList<int[]>();
    private static int queuePos;
    private static boolean scanning;
    private static long lastScanTime = -100000L;
    private static int lastMask = Integer.MIN_VALUE;
    private static int scanMask;
    private static double ox, oy, oz;
    private static int radius, height;

    private static int[] found = new int[0];
    private static double[] nearest = new double[0];

    private static final Map<IBlockState, Integer> CACHE = new HashMap<IBlockState, Integer>();
    private static int cacheHash;

    private OreScannerClient() {}

    private static void reset() {
        ores = new ArrayList<Hit>();
        pending.clear();
        queue.clear();
        scanning = false;
        lastScanTime = -100000L;
        lastMask = Integer.MIN_VALUE;
        found = new int[0];
        nearest = new double[0];
    }

    public static void clientTick(Minecraft mc) {
        if (mc.world == null || mc.player == null || !OreScanner.isOn(mc.player)) {
            if (!ores.isEmpty() || scanning || found.length > 0) reset();
            return;
        }
        try {
            step(mc);
        } catch (Throwable t) {
            // сканер не должен ронять игру
            System.err.println("[PocketMod] ore scan failed: " + t);
            reset();
            lastScanTime = mc.world.getTotalWorldTime();
        }
    }

    private static void step(Minecraft mc) {
        int hash = Arrays.hashCode(PocketConfig.oreScannerTargets);
        if (hash != cacheHash) {
            CACHE.clear();
            cacheHash = hash;
            lastMask = Integer.MIN_VALUE;
        }
        int mask = OreScanner.disabledMask(mc.player);
        long now = mc.world.getTotalWorldTime();
        if (!scanning && (mask != lastMask || now < lastScanTime || now - lastScanTime >= PERIOD_TICKS)) {
            startScan(mc, mask);
        }
        if (!scanning) return;
        for (int n = 0; n < CHUNKS_PER_TICK && queuePos < queue.size(); n++) {
            int[] c = queue.get(queuePos++);
            scanChunk(mc, c[0], c[1]);
        }
        if (queuePos >= queue.size()) finishScan(mc, now);
    }

    private static void startScan(Minecraft mc, int mask) {
        radius = OreScanner.radius(mc.player);
        height = PocketConfig.oreScannerVertical;
        ox = mc.player.posX;
        oy = mc.player.posY;
        oz = mc.player.posZ;
        scanMask = mask;
        pending.clear();
        queue.clear();
        queuePos = 0;
        int px = (int) Math.floor(ox);
        int pz = (int) Math.floor(oz);
        for (int cx = (px - radius) >> 4; cx <= (px + radius) >> 4; cx++) {
            for (int cz = (pz - radius) >> 4; cz <= (pz + radius) >> 4; cz++) {
                queue.add(new int[] { cx, cz });
            }
        }
        final int fpx = px;
        final int fpz = pz;
        Collections.sort(queue, new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                double da = Math.pow(a[0] * 16 + 8 - fpx, 2) + Math.pow(a[1] * 16 + 8 - fpz, 2);
                double db = Math.pow(b[0] * 16 + 8 - fpx, 2) + Math.pow(b[1] * 16 + 8 - fpz, 2);
                return Double.compare(da, db);
            }
        });
        scanning = true;
    }

    private static void scanChunk(Minecraft mc, int cx, int cz) {
        Chunk chunk = mc.world.getChunkProvider().getLoadedChunk(cx, cz);
        if (chunk == null || chunk.isEmpty()) return;
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        int py = (int) Math.floor(oy);
        int minY = Math.max(0, py - height);
        int maxY = Math.min(255, py + height);
        double r2 = (double) radius * radius;
        for (int sec = minY >> 4; sec <= (maxY >> 4) && sec < sections.length; sec++) {
            ExtendedBlockStorage s = sections[sec];
            if (s == null || s == Chunk.NULL_BLOCK_STORAGE || s.isEmpty()) continue;
            int baseY = sec << 4;
            for (int y = 0; y < 16; y++) {
                int wy = baseY + y;
                if (wy < minY || wy > maxY) continue;
                for (int z = 0; z < 16; z++) {
                    int wz = (cz << 4) + z;
                    double dz = wz + 0.5 - oz;
                    for (int x = 0; x < 16; x++) {
                        int wx = (cx << 4) + x;
                        double dx = wx + 0.5 - ox;
                        double d2 = dx * dx + dz * dz;
                        if (d2 > r2) continue;
                        IBlockState state = s.get(x, y, z);
                        if (state.getBlock() == Blocks.AIR) continue;
                        int idx = indexOf(state);
                        if (idx < 0 || (scanMask & (1 << idx)) != 0) continue;
                        double dy = wy + 0.5 - oy;
                        pending.add(new Hit(new BlockPos(wx, wy, wz), idx, d2 + dy * dy));
                    }
                }
            }
        }
    }

    private static void finishScan(Minecraft mc, long now) {
        int n = Math.min(PocketConfig.oreScannerTargets.length, MAX_TARGETS);
        int[] cnt = new int[n];
        double[] near = new double[n];
        Arrays.fill(near, Double.MAX_VALUE);
        for (Hit h : pending) {
            if (h.idx >= n) continue;
            cnt[h.idx]++;
            double d = Math.sqrt(h.d2);
            if (d < near[h.idx]) near[h.idx] = d;
        }
        Collections.sort(pending, new Comparator<Hit>() {
            @Override
            public int compare(Hit a, Hit b) {
                return Double.compare(a.d2, b.d2);
            }
        });
        List<Hit> result = new ArrayList<Hit>(Math.min(pending.size(), MAX_MARKS));
        for (int i = 0; i < pending.size() && i < MAX_MARKS; i++) result.add(pending.get(i));
        ores = result;
        found = cnt;
        nearest = near;
        pending.clear();
        queue.clear();
        scanning = false;
        lastScanTime = now;
        lastMask = scanMask;
    }

    /** Индекс цели для блока (или -1). Результат кэшируется по состоянию блока. */
    private static int indexOf(IBlockState state) {
        Integer cached = CACHE.get(state);
        if (cached != null) return cached.intValue();
        int v = computeIndex(state);
        CACHE.put(state, Integer.valueOf(v));
        return v;
    }

    private static int computeIndex(IBlockState state) {
        Block b = state.getBlock();
        if (b == Blocks.AIR) return -1;
        String[] targets = PocketConfig.oreScannerTargets;
        int n = Math.min(targets.length, MAX_TARGETS);
        ResourceLocation key = b.getRegistryName();
        if (key != null) {
            String full = key.toString();
            for (int i = 0; i < n; i++) {
                if (targets[i] != null && targets[i].indexOf(':') >= 0 && targets[i].trim().equalsIgnoreCase(full)) return i;
            }
        }
        // у воды, лавы, огня и т.п. нет предмета: пустой ItemStack делает OreDictionary.getOreIDs исключением
        Item item = Item.getItemFromBlock(b);
        if (item == null || item == Items.AIR) return -1;
        ItemStack stack = new ItemStack(item, 1, b.damageDropped(state));
        if (stack.isEmpty()) return -1;
        for (int id : OreDictionary.getOreIDs(stack)) {
            String name = OreDictionary.getOreName(id);
            for (int i = 0; i < n; i++) {
                if (targets[i] != null && targets[i].trim().equalsIgnoreCase(name)) return i;
            }
        }
        return -1;
    }

    public static int colorOf(int idx) {
        String[] t = PocketConfig.oreScannerTargets;
        String p = idx >= 0 && idx < t.length && t[idx] != null ? t[idx].toLowerCase() : "";
        if (p.contains("diamond")) return 0x4DEBFF;
        if (p.contains("emerald")) return 0x3DFF6B;
        if (p.contains("gold")) return 0xFFD83A;
        if (p.contains("iron")) return 0xE8B08C;
        if (p.contains("coal")) return 0xB0B0B0;
        if (p.contains("lapis")) return 0x3F6BFF;
        if (p.contains("redstone")) return 0xFF3030;
        if (p.contains("quartz")) return 0xF2F2F2;
        if (p.contains("copper")) return 0xE08A4A;
        if (p.contains("tin")) return 0xB8C8D8;
        if (p.contains("silver")) return 0xDADAF0;
        if (p.contains("lead")) return 0x7080B0;
        return 0xFF55FF;
    }

    /** Читаемое название цели: из перевода pocketmod.ore.<имя> или само имя без приставки ore. */
    public static String displayName(String target) {
        String key = "pocketmod.ore." + target;
        String s = Tr.format(key);
        if (s != null && !s.equals(key)) return s;
        String t = target == null ? "?" : target;
        int c = t.indexOf(':');
        if (c >= 0) t = t.substring(c + 1);
        if (t.startsWith("ore") && t.length() > 3) t = t.substring(3);
        return t;
    }

    /** Сводка на экране: что найдено, сколько штук и расстояние до ближайшей. */
    public static void drawHud(Minecraft mc) {
        if (mc.player == null || !OreScanner.isOn(mc.player)) return;
        String[] t = PocketConfig.oreScannerTargets;
        int mask = OreScanner.disabledMask(mc.player);
        int x = 10;
        int y = 26;
        mc.fontRenderer.drawStringWithShadow(Tr.format("pocketmod.hud.scanner"), x, y, 0xFFFFFF);
        y += 11;
        boolean any = false;
        for (int i = 0; i < t.length && i < MAX_TARGETS; i++) {
            if ((mask & (1 << i)) != 0) continue;
            int cnt = i < found.length ? found[i] : 0;
            if (cnt <= 0) continue;
            any = true;
            Gui.drawRect(x, y + 1, x + 6, y + 8, 0xFF000000 | colorOf(i));
            String line = displayName(t[i]) + " x" + cnt + " (" + (int) Math.round(nearest[i]) + " " + Tr.format("pocketmod.hud.m") + ")";
            mc.fontRenderer.drawStringWithShadow(line, x + 10, y, 0xFFFFFF);
            y += 10;
        }
        if (!any) {
            mc.fontRenderer.drawStringWithShadow(Tr.format(scanning || found.length == 0 ? "pocketmod.hud.scanner_wait" : "pocketmod.hud.scanner_none"), x, y, 0x9AA5B1);
        }
    }

    public static void render(Minecraft mc) {
        if (mc.world == null || mc.player == null || !OreScanner.isOn(mc.player) || ores.isEmpty()) return;
        RenderManager manager = mc.getRenderManager();
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glLineWidth(2.0F);
        for (Hit h : ores) {
            // добытая руда пропадает сразу, не дожидаясь пересчёта
            if (indexOf(mc.world.getBlockState(h.pos)) != h.idx) continue;
            int c = colorOf(h.idx);
            float r = ((c >> 16) & 255) / 255.0F;
            float g = ((c >> 8) & 255) / 255.0F;
            float b = (c & 255) / 255.0F;
            AxisAlignedBB box = new AxisAlignedBB(h.pos).grow(0.003)
                    .offset(-manager.viewerPosX, -manager.viewerPosY, -manager.viewerPosZ);
            RenderGlobal.drawSelectionBoundingBox(box, r, g, b, 0.95F);
        }
        GL11.glLineWidth(1.0F);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
