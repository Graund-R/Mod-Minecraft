package pocketmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Рывок на клиенте: распознаёт двойное нажатие W/A/S/D и, когда сервер подтвердил рывок (новый номер рывка в данных
 * игрока), задаёт скорость на 5 тиков. Столкновения с блоками обрабатывает обычная физика игрока.
 */
@SideOnly(Side.CLIENT)
public class ClientDash {

    private static final long WINDOW_MS = 250L;

    private final long[] last = new long[4];
    private final boolean[] down = new boolean[4];
    private int lastId = Integer.MIN_VALUE;
    private int ticksLeft;
    private double dx;
    private double dz;
    private EntityPlayerSP lastPlayer;

    private static boolean raw(KeyBinding k) {
        int c = k.getKeyCode();
        if (c == 0) return false;
        if (c < 0) return Mouse.isButtonDown(c + 100);
        return Keyboard.isKeyDown(c);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;
        if (p == null || mc.currentScreen != null) {
            for (int i = 0; i < 4; i++) down[i] = false;
            return;
        }
        GameSettings gs = mc.gameSettings;
        KeyBinding[] keys = { gs.keyBindForward, gs.keyBindBack, gs.keyBindLeft, gs.keyBindRight };
        for (int i = 0; i < 4; i++) {
            boolean d = raw(keys[i]);
            if (d && !down[i]) {
                long now = System.currentTimeMillis();
                if (last[i] > 0L && now - last[i] <= WINDOW_MS) {
                    last[i] = 0L;
                    tryDash(p, i);
                } else {
                    last[i] = now;
                }
            }
            down[i] = d;
        }
    }

    private void tryDash(EntityPlayerSP p, int dir) {
        if (ticksLeft > 0 || !Dash.allowed(p)) return;
        NetworkHandler.action(NetAction.DASH, dir, 0, 0, "");
    }

    @SubscribeEvent
    public void onInput(InputUpdateEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;
        if (p == null || event.getEntityPlayer() != p) return;
        int id = p.getEntityData().getInteger(Dash.ID);
        if (p != lastPlayer) {
            lastPlayer = p;
            lastId = id;
            ticksLeft = 0;
        }
        if (id != lastId) {
            lastId = id;
            ticksLeft = PocketConfig.dashTicks;
            dx = p.getEntityData().getFloat(Dash.DX);
            dz = p.getEntityData().getFloat(Dash.DZ);
        }
        if (ticksLeft <= 0) return;
        if (Spector.isActive(p) || p.isSpectator()) {
            ticksLeft = 0;
            return;
        }
        p.motionX = dx * PocketConfig.dashSpeed;
        p.motionZ = dz * PocketConfig.dashSpeed;
        ticksLeft--;
    }
}
