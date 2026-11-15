package pocketmod;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumParticleTypes;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Маркеры тотемов: клиентские «светлячки» (частицы) примерно в 2 блоках над землёй.
 * Разные цвета по порядку в списке, лёгкое парение и мерцание. Сущностей и блоков нет.
 */
@SideOnly(Side.CLIENT)
public class TotemMarkers {

    public static void tick(Minecraft mc) {
        if (mc.world == null || mc.player == null || mc.isGamePaused() || !mc.isIntegratedServerRunning()) return;
        try {
            EntityPlayerMP sp = ClientHandler.sp();
            if (sp == null) return;
            List<Totems.Totem> list = Totems.get(sp);
            if (list.isEmpty()) return;

            int dim = mc.player.dimension;
            long time = mc.world.getTotalWorldTime();
            double range2 = (double) PocketConfig.totemMarkerRange * PocketConfig.totemMarkerRange;

            for (int i = 0; i < list.size(); i++) {
                Totems.Totem t = list.get(i);
                if (t.dim != dim) continue;
                double dx = t.x - mc.player.posX;
                double dz = t.z - mc.player.posZ;
                if (dx * dx + dz * dz > range2) continue;

                int rgb = PocketConfig.totemColor(i);
                float r = Math.max(0.05f, ((rgb >> 16) & 0xFF) / 255.0f);
                float g = ((rgb >> 8) & 0xFF) / 255.0f;
                float b = (rgb & 0xFF) / 255.0f;

                double bob = Math.sin((time + i * 7) * 0.1) * 0.15;
                double cy = t.y + 2.2 + bob;
                java.util.Random rnd = mc.world.rand;

                // ядро: цветная пыль, редкое мерцание
                if (time % 2 == 0) {
                    mc.world.spawnParticle(EnumParticleTypes.REDSTONE,
                            t.x + (rnd.nextDouble() - 0.5) * 0.12, cy + (rnd.nextDouble() - 0.5) * 0.12,
                            t.z + (rnd.nextDouble() - 0.5) * 0.12, r, g, b);
                }
                // медленный «орбитальный» светлячок
                double a = (time + i * 13) * 0.12;
                mc.world.spawnParticle(EnumParticleTypes.REDSTONE,
                        t.x + Math.cos(a) * 0.35, cy + Math.sin(a * 1.3) * 0.15, t.z + Math.sin(a) * 0.35, r, g, b);
                // искра
                if (time % 8 == (i % 8)) {
                    mc.world.spawnParticle(EnumParticleTypes.END_ROD, t.x, cy, t.z, 0.0, 0.02, 0.0);
                }
            }
        } catch (Exception ignored) {
            // данные сервера читаются из клиентского потока — редкая гонка не страшна
        }
    }
}
