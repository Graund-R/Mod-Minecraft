package pocketmod;

import java.util.EnumSet;
import java.util.List;

import journeymap.client.api.ClientPlugin;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.display.DisplayType;
import journeymap.client.api.display.Waypoint;
import journeymap.client.api.event.ClientEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;

/** Метки тотемов в JourneyMap. Копируется в сборку только если JourneyMap API удалось подключить. */
@ClientPlugin
public class JmPlugin implements IClientPlugin {

    private IClientAPI api;
    private String lastState;

    @Override
    public void initialize(IClientAPI jmClientApi) {
        this.api = jmClientApi;
        jmClientApi.subscribe(getModId(), EnumSet.of(ClientEvent.Type.MAPPING_STARTED, ClientEvent.Type.DISPLAY_UPDATE));
        JmHook.refresh = new Runnable() {
            @Override
            public void run() {
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        sync();
                    }
                });
            }
        };
    }

    @Override
    public String getModId() {
        return PocketMod.MODID;
    }

    @Override
    public void onEvent(ClientEvent event) {
        if (event.type == ClientEvent.Type.MAPPING_STARTED || event.type == ClientEvent.Type.DISPLAY_UPDATE) {
            sync();
        }
    }

    private void sync() {
        if (api == null) return;
        try {
            EntityPlayer player = Minecraft.getMinecraft().player;
            if (player == null) {
                if (lastState != null) {
                    api.removeAll(PocketMod.MODID, DisplayType.Waypoint);
                    lastState = null;
                }
                return;
            }

            List<Totems.Totem> list = Totems.get(player);
            StringBuilder stateBuilder = new StringBuilder();
            for (Totems.Totem t : list) {
                stateBuilder.append(t.name).append('|').append(t.dim).append('|')
                        .append(Double.doubleToLongBits(t.x)).append('|')
                        .append(Double.doubleToLongBits(t.y)).append('|')
                        .append(Double.doubleToLongBits(t.z)).append(';');
            }
            String state = stateBuilder.toString();
            if (state.equals(lastState)) return;

            api.removeAll(PocketMod.MODID, DisplayType.Waypoint);
            for (int i = 0; i < list.size(); i++) {
                Totems.Totem t = list.get(i);
                Waypoint w = new Waypoint(PocketMod.MODID, "totem_" + i, t.name, t.dim,
                        new BlockPos(Math.floor(t.x), Math.floor(t.y), Math.floor(t.z)));
                w.setColor(PocketConfig.totemColor(i));
                w.setPersistent(false);
                api.show(w);
            }
            lastState = state;
        } catch (Throwable ignored) {
        }
    }
}
