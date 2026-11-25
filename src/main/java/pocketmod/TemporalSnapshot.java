package pocketmod;

import java.io.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Vanilla world state, inventories and Pocket player data. Never reads arbitrary mod capabilities. */
final class TemporalSnapshot {
    final Map<UUID,NBTTagCompound> entities=new LinkedHashMap<UUID,NBTTagCompound>();
    final Map<UUID,NBTTagCompound> players=new LinkedHashMap<UUID,NBTTagCompound>();
    final Map<BlockPos,NBTTagCompound> tiles=new LinkedHashMap<BlockPos,NBTTagCompound>();
    final Map<BlockPos,IBlockState> before=new LinkedHashMap<BlockPos,IBlockState>();
    final Map<BlockPos,IBlockState> after=new LinkedHashMap<BlockPos,IBlockState>();
    final Map<BlockPos,NBTTagCompound> beforeTiles=new LinkedHashMap<BlockPos,NBTTagCompound>();
    final Map<BlockPos,NBTTagCompound> afterTiles=new LinkedHashMap<BlockPos,NBTTagCompound>();
    NBTTagCompound exile=new NBTTagCompound();
    final Set<Long> chunks=new HashSet<Long>();
    final List<net.minecraft.block.BlockEventData> blockEvents=new ArrayList<net.minecraft.block.BlockEventData>();
    final List<TemporalSystem.Hit> hits=new ArrayList<TemporalSystem.Hit>();
    final List<net.minecraft.world.NextTickListEntry> scheduled=new ArrayList<net.minecraft.world.NextTickListEntry>();
    long clock;
    long day; int rain,thunder,clean;boolean raining,thundering;
    int bytes;
    static TemporalSnapshot capture(WorldServer world,TemporalSystem.Track track) throws IOException {
        TemporalSnapshot f=new TemporalSnapshot();
        CountingStream count=new CountingStream();DataOutputStream out=new DataOutputStream(count);
        f.day=world.getWorldTime();f.clock=world.getTotalWorldTime();
        net.minecraft.world.storage.WorldInfo info=world.getWorldInfo();
        f.rain=info.getRainTime();f.thunder=info.getThunderTime();f.clean=info.getCleanWeatherTime();
        f.raining=info.isRaining();f.thundering=info.isThundering();
        for(net.minecraft.world.chunk.Chunk c:world.getChunkProvider().getLoadedChunks()) {
            f.chunks.add(net.minecraft.util.math.ChunkPos.asLong(c.x,c.z));
            List<net.minecraft.world.NextTickListEntry> pending=world.getPendingBlockUpdates(c,false);
            if(pending!=null) f.scheduled.addAll(pending);
        }
        if(f.scheduled.size()>8192) throw new IOException("scheduled tick limit");
        for(Entity e:new ArrayList<Entity>(world.loadedEntityList)) {
            if(e.isDead || e instanceof EntityPlayer)continue;
            NBTTagCompound n=new NBTTagCompound();
            net.minecraft.util.ResourceLocation type=EntityList.getKey(e);if(type==null)continue;
            e.writeToNBT(n);n.setString("id",type.toString());n.removeTag("Passengers");
            if(e.isRiding())n.setUniqueId("pocket_vehicle",e.getRidingEntity().getUniqueID());
            f.entities.put(e.getUniqueID(),n);CompressedStreamTools.write(n,out);
            if(f.entities.size()>1024)throw new IOException("entity limit");
        }
        f.exile=ExileWorldData.get(world.getMinecraftServer().getWorld(0)).writeToNBT(new NBTTagCompound());CompressedStreamTools.write(f.exile,out);
        for(EntityPlayer p:world.playerEntities) {
            if(!(p instanceof EntityPlayerMP))continue;
            NBTTagCompound n=new NBTTagCompound();
            n.setDouble("x",p.posX);n.setDouble("y",p.posY);n.setDouble("z",p.posZ);
            n.setFloat("yaw",p.rotationYaw);n.setFloat("pitch",p.rotationPitch);
            n.setDouble("mx",p.motionX);n.setDouble("my",p.motionY);n.setDouble("mz",p.motionZ);
            n.setFloat("health",p.getHealth());n.setFloat("absorption",p.getAbsorptionAmount());
            n.setInteger("fire",p.isBurning()?100:0);n.setInteger("air",p.getAir());n.setFloat("fall",p.fallDistance);
            n.setTag("cursor",p.inventory.getItemStack().writeToNBT(new NBTTagCompound()));
            n.setTag("ender",p.getInventoryEnderChest().saveInventoryToNBT());
            n.setInteger("game",((EntityPlayerMP)p).interactionManager.getGameType().getID());
            p.capabilities.writeCapabilitiesToNBT(n);
            n.setTag("inventory",p.inventory.writeToNBT(new NBTTagList()));n.setInteger("slot",p.inventory.currentItem);
            NBTTagCompound food=new NBTTagCompound();p.getFoodStats().writeNBT(food);n.setTag("food",food);
            n.setInteger("xp",p.experienceTotal);n.setInteger("level",p.experienceLevel);n.setFloat("xpbar",p.experience);
            NBTTagList effects=new NBTTagList();for(PotionEffect effect:p.getActivePotionEffects())effects.appendTag(effect.writeCustomPotionEffectToNBT(new NBTTagCompound()));
            n.setTag("effects",effects);NBTTagCompound pocket=PlayerData.root(p).copy();SyncedState.write(p,pocket);n.setTag("pocket",pocket);
            f.players.put(p.getUniqueID(),n);CompressedStreamTools.write(n,out);
        }
        for(TileEntity tile:new ArrayList<TileEntity>(world.loadedTileEntityList)) {
            if(tile.isInvalid())continue;
            NBTTagCompound n=tile.writeToNBT(new NBTTagCompound());CompressedStreamTools.write(n,out);f.tiles.put(tile.getPos().toImmutable(),n);
            if(f.tiles.size()>1024)throw new IOException("tile entity limit");
        }
        f.before.putAll(track.changed);f.beforeTiles.putAll(track.changedTiles);
        for(BlockPos pos:f.before.keySet()) { f.after.put(pos,world.getBlockState(pos));TileEntity tile=world.getTileEntity(pos);if(tile!=null)f.afterTiles.put(pos,tile.writeToNBT(new NBTTagCompound())); }
        f.hits.addAll(track.hits);f.blockEvents.addAll(TemporalQueues.copy(world));
        if(f.blockEvents.size()>8192)throw new IOException("block event limit");
        // Count raw NBT without allocating a second copy. Keep a strict per-dimension history budget.

        for(NBTTagCompound n:f.beforeTiles.values())CompressedStreamTools.write(n,out);
        for(NBTTagCompound n:f.afterTiles.values())CompressedStreamTools.write(n,out);
        f.bytes=count.count+f.before.size()*64+f.scheduled.size()*48+f.hits.size()*64+f.blockEvents.size()*48;
        if(f.bytes>4*1024*1024)throw new IOException("snapshot limit");
        return f;
    }
    void blocks(WorldServer world,boolean backwards) {
        List<Map.Entry<BlockPos,IBlockState>> changes=new ArrayList<Map.Entry<BlockPos,IBlockState>>((backwards?before:after).entrySet());
        if(backwards)Collections.reverse(changes);
        for(Map.Entry<BlockPos,IBlockState> c:changes) if(world.isBlockLoaded(c.getKey())) {
            world.setBlockState(c.getKey(),c.getValue(),2);
            NBTTagCompound n=(backwards?beforeTiles:afterTiles).get(c.getKey());TileEntity tile=world.getTileEntity(c.getKey());
            if(tile!=null&&n!=null){tile.readFromNBT(n.copy());tile.markDirty();world.notifyBlockUpdate(c.getKey(),c.getValue(),c.getValue(),3);}
        }
    }
    void restore(WorldServer world,TemporalSystem.Track track) {
        for(Entity e:new ArrayList<Entity>(world.loadedEntityList)) {
            if(e instanceof EntityPlayer || !chunks.contains(net.minecraft.util.math.ChunkPos.asLong(e.chunkCoordX,e.chunkCoordZ)))continue;
            if(!entities.containsKey(e.getUniqueID()))world.removeEntityDangerously(e);
        }
        for(Entity e:world.loadedEntityList)if(!(e instanceof EntityPlayer))e.dismountRidingEntity();
        Map<UUID,Entity> current=new HashMap<UUID,Entity>();
        for(Entity e:world.loadedEntityList)if(!e.isDead)current.put(e.getUniqueID(),e);
        for(Map.Entry<UUID,NBTTagCompound> entry:entities.entrySet()) {
            NBTTagCompound n=entry.getValue().copy();patchItems(n,track);NBTTagList pos=n.getTagList("Pos",6);
            if(!world.isBlockLoaded(new BlockPos(pos.getDoubleAt(0),pos.getDoubleAt(1),pos.getDoubleAt(2))))continue;
            Entity e=current.get(entry.getKey());
            if(e==null) {e=EntityList.createEntityFromNBT(n.copy(),world);if(e!=null){track.restoringSpawn=true;try{world.spawnEntity(e);}finally{track.restoringSpawn=false;}}}
            else {e.readFromNBT(n.copy());e.setPosition(e.posX,e.posY,e.posZ);e.velocityChanged=true;}
        }
        for(Map.Entry<UUID,NBTTagCompound> entry:entities.entrySet()) {
            if(!entry.getValue().hasUniqueId("pocket_vehicle"))continue;
            Entity rider=world.getEntityFromUuid(entry.getKey()),vehicle=world.getEntityFromUuid(entry.getValue().getUniqueId("pocket_vehicle"));
            if(rider!=null&&vehicle!=null)rider.startRiding(vehicle,true);
        }
        for(Map.Entry<BlockPos,NBTTagCompound> entry:tiles.entrySet()) {
            if(!world.isBlockLoaded(entry.getKey()))continue;
            TileEntity tile=world.getTileEntity(entry.getKey());
            if(tile!=null) {
                NBTTagCompound data=entry.getValue().copy();patchItems(data,track);tile.readFromNBT(data);tile.markDirty();
                IBlockState state=world.getBlockState(entry.getKey());world.notifyBlockUpdate(entry.getKey(),state,state,3);
            }
        }
        for(EntityPlayer p:world.playerEntities) {
            NBTTagCompound n=players.get(p.getUniqueID());if(n==null || !(p instanceof EntityPlayerMP))continue;
            EntityPlayerMP player=(EntityPlayerMP)p;
            if(player.openContainer!=player.inventoryContainer)player.closeScreen();
            n=n.copy();patchItems(n,track);
            player.dismountRidingEntity();player.setGameType(net.minecraft.world.GameType.getByID(n.getInteger("game")));
            player.capabilities.readCapabilitiesFromNBT(n);player.sendPlayerAbilities();
            player.inventory.setItemStack(new ItemStack(n.getCompoundTag("cursor")));
            player.getInventoryEnderChest().loadInventoryFromNBT(n.getTagList("ender",10));
            player.inventory.readFromNBT(n.getTagList("inventory",10));player.inventory.currentItem=n.getInteger("slot");
            // The active dial is a resource outside the timeline. Its charge cannot be restored by rewind.
            for(ItemStack item:player.inventory.mainInventory)patchDial(item,track);
            for(ItemStack item:player.inventory.offHandInventory)patchDial(item,track);
            player.setHealth(Math.max(1,n.getFloat("health")));player.setAbsorptionAmount(n.getFloat("absorption"));
            player.extinguish();if(n.getInteger("fire")>0)player.setFire(5);
            player.setAir(n.getInteger("air"));player.fallDistance=n.getFloat("fall");
            player.getFoodStats().readNBT(n.getCompoundTag("food"));
            player.experienceTotal=n.getInteger("xp");player.experienceLevel=n.getInteger("level");player.experience=n.getFloat("xpbar");
            player.clearActivePotions();NBTTagList effects=n.getTagList("effects",10);
            for(int i=0;i<effects.tagCount();i++){PotionEffect effect=PotionEffect.readCustomPotionEffectFromNBT(effects.getCompoundTagAt(i));if(effect!=null)player.addPotionEffect(effect);}
            PlayerData.replace(player,n.getCompoundTag("pocket"));
            player.connection.setPlayerLocation(n.getDouble("x"),n.getDouble("y"),n.getDouble("z"),n.getFloat("yaw"),n.getFloat("pitch"));
            player.motionX=n.getDouble("mx");player.motionY=n.getDouble("my");player.motionZ=n.getDouble("mz");
            player.velocityChanged=true;player.inventory.markDirty();player.sendContainerToPlayer(player.inventoryContainer);
            player.connection.sendPacket(new net.minecraft.network.play.server.SPacketSetExperience(player.experience,player.experienceTotal,player.experienceLevel));
            NetworkHandler.sync(player);
        }
        for(net.minecraft.world.chunk.Chunk c:world.getChunkProvider().getLoadedChunks())world.getPendingBlockUpdates(c,true);
        ExileWorldData data=ExileWorldData.get(world.getMinecraftServer().getWorld(0));data.readFromNBT(exile.copy());data.markDirty();
        for(net.minecraft.world.NextTickListEntry e:scheduled)if(world.isBlockLoaded(e.position))
            world.scheduleBlockUpdate(e.position,e.getBlock(),(int)Math.max(1,Math.min(Integer.MAX_VALUE,e.scheduledTime-clock)),e.priority);
        TemporalQueues.restore(world,blockEvents);
        world.setWorldTime(day);
        net.minecraft.world.storage.WorldInfo info=world.getWorldInfo();
        info.setRainTime(rain);info.setThunderTime(thunder);info.setCleanWeatherTime(clean);info.setRaining(raining);info.setThundering(thundering);
        track.hits.clear();track.hits.addAll(hits);
    }
    static void patchItems(NBTBase value,TemporalSystem.Track track) {
        if(value instanceof NBTTagCompound) {
            NBTTagCompound n=(NBTTagCompound)value;
            if(n.getString("id").equals("pocketmod:temporal_dial") && n.getCompoundTag("tag").getString("dial_id").equals(track.dialId))n.setTag("tag",track.dial.copy());
            for(String key:new ArrayList<String>(n.getKeySet()))patchItems(n.getTag(key),track);
        }else if(value instanceof NBTTagList){NBTTagList list=(NBTTagList)value;for(int i=0;i<list.tagCount();i++)patchItems(list.get(i),track);}
    }
    private static void patchDial(ItemStack item,TemporalSystem.Track track) {
        if(TemporalDialItem.isDial(item) && TemporalDialItem.data(item).getString("dial_id").equals(track.dialId))item.setTagCompound(track.dial.copy());
    }
    private static final class CountingStream extends OutputStream {
        int count; public void write(int b)throws IOException{count++;check();} public void write(byte[] b,int off,int len)throws IOException{count+=len;check();}
        private void check()throws IOException{if(count>4*1024*1024)throw new IOException("snapshot byte limit");}
    }
}
