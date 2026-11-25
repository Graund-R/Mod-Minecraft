package pocketmod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Called only after vanilla classes have loaded; the transformer itself never loads Minecraft. */
public final class TemporalHooks {
    private TemporalHooks() {}
    public static boolean frozenWorld(Object world) { return world instanceof World && TemporalSystem.frozen((World)world); }
    public static boolean skipEntity(Object object) {
        if (!(object instanceof Entity)) return false;
        Entity e=(Entity)object;
        boolean skip=frozenWorld(e.world) && !TemporalSystem.liveItem(e) && (!(e instanceof EntityPlayer) || !TemporalSystem.canMove((EntityPlayer)e));
        if(skip&&e.world.isRemote)settle(e);
        return skip;
    }
    public static void settle(Entity e) {
        e.prevPosX=e.lastTickPosX=e.posX;e.prevPosY=e.lastTickPosY=e.posY;e.prevPosZ=e.lastTickPosZ=e.posZ;
        e.prevRotationYaw=e.rotationYaw;e.prevRotationPitch=e.rotationPitch;
        if(e instanceof net.minecraft.entity.EntityLivingBase) {
            net.minecraft.entity.EntityLivingBase living=(net.minecraft.entity.EntityLivingBase)e;
            living.prevLimbSwingAmount=living.limbSwingAmount=0;
            living.prevRenderYawOffset=living.renderYawOffset;living.prevRotationYawHead=living.rotationYawHead;
            living.prevSwingProgress=living.swingProgress;
        }
    }
    public static void beginTile(Object tile){ExileGrayClient.beginTile(tile);}
    public static void endTile(){ExileGrayClient.endTile();}
    public static void tickWeatherEntity(Object entity) {if(!skipEntity(entity))((Entity)entity).onUpdate();}
    public static void tickTile(Object tile) {
        if (tile instanceof TileEntity && frozenWorld(((TileEntity)tile).getWorld()) && !TemporalSystem.tileAwake((TileEntity)tile)) return;
        ((ITickable)tile).update();
    }
    public static boolean beforeBlock(Object world,Object pos) {
        return world instanceof World && pos instanceof BlockPos && TemporalSystem.beforeBlock((World)world,(BlockPos)pos);
    }
    public static boolean skipConnection(Object connection) {return connection instanceof net.minecraft.network.NetHandlerPlayServer && !TemporalSystem.canMove(((net.minecraft.network.NetHandlerPlayServer)connection).player);}
    public static boolean skipClick(Object connection,Object packet) {
        net.minecraft.network.NetHandlerPlayServer handler=(net.minecraft.network.NetHandlerPlayServer)connection;
        net.minecraft.network.play.client.CPacketClickWindow click=(net.minecraft.network.play.client.CPacketClickWindow)packet;
        net.minecraft.entity.player.EntityPlayerMP p=handler.player;
        if(!TemporalSystem.busy(p))return !TemporalSystem.canMove(p);
        net.minecraft.inventory.Container container=p.openContainer;
        int slot=click.getSlotId();
        boolean reject=!TemporalSystem.canMove(p)||TemporalSystem.pinned(p,p.inventory.getItemStack());
        if(slot>=0 && slot<container.inventorySlots.size())reject|=TemporalSystem.pinned(p,container.getSlot(slot).getStack());
        if(click.getClickType()==net.minecraft.inventory.ClickType.SWAP && click.getUsedButton()>=0 && click.getUsedButton()<9)
            reject|=TemporalSystem.pinned(p,p.inventory.getStackInSlot(click.getUsedButton()));
        if(reject){handler.sendPacket(new net.minecraft.network.play.server.SPacketConfirmTransaction(click.getWindowId(),click.getActionNumber(),false));p.sendContainerToPlayer(container);}
        return reject;
    }
    public static boolean frozenClient() { return TemporalSystem.clientFrozen; }
}
