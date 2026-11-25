package pocketmod;

import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.tileentity.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.storage.*;

/** Real snapshots and per-tick reverse restoration, with only an in-memory WorldServer fixture. */
public final class TemporalBranchesSmoke {
    static int checks;
    static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
    static class ServerFixture extends DedicatedServer {
        ServerFixture(){super(null,null,null,null,null,null);}
        @Override public WorldServer getWorld(int dimension){return worlds[0];}
    }
    static class Provider extends ChunkProviderServer {
        Collection<Chunk> chunks;
        Provider(){super(null,null,null);}
        @Override public Collection<Chunk> getLoadedChunks(){return chunks;}
    }
    static class WorldFixture extends WorldServer {
        Map<BlockPos,IBlockState> blocks;
        Map<BlockPos,TileEntity> tiles;
        WorldInfo info;MapStorage storage;Provider providerFixture;MinecraftServer server;long clock;
        WorldFixture(){super(null,null,null,0,null);}
        @Override public Chunk getChunkFromChunkCoords(int x,int z){return providerFixture.chunks.iterator().next();}
        @Override public Chunk getChunkFromBlockCoords(BlockPos pos){return getChunkFromChunkCoords(pos.getX()>>4,pos.getZ()>>4);}
        @Override public void markChunkDirty(BlockPos pos,TileEntity tile){}
        @Override public WorldInfo getWorldInfo(){return info;}
        @Override public MapStorage getMapStorage(){return storage;}
        @Override public MapStorage getPerWorldStorage(){return storage;}
        @Override public ChunkProviderServer getChunkProvider(){return providerFixture;}
        @Override public MinecraftServer getMinecraftServer(){return server;}
        @Override public List<NextTickListEntry> getPendingBlockUpdates(Chunk c,boolean remove){return Collections.emptyList();}
        @Override public void scheduleBlockUpdate(BlockPos p,net.minecraft.block.Block b,int delay,int priority){}
        @Override public long getTotalWorldTime(){return clock;}
        @Override public long getWorldTime(){return info.getWorldTime();}
        @Override public void setWorldTime(long value){info.setWorldTime(value);}
        @Override public boolean isBlockLoaded(BlockPos p){return true;}
        @Override public boolean isAreaLoaded(BlockPos a,BlockPos b){return true;}
        @Override public IBlockState getBlockState(BlockPos p){return blocks.getOrDefault(p,Blocks.AIR.getDefaultState());}
        @Override public boolean setBlockState(BlockPos p,IBlockState state,int flags){blocks.put(p,state);return true;}
        @Override public TileEntity getTileEntity(BlockPos p){return tiles.get(p);}
        @Override public boolean spawnEntity(Entity e){loadedEntityList.add(e);return true;}
        @Override public void removeEntityDangerously(Entity e){loadedEntityList.remove(e);e.isDead=true;}
        @Override public Entity getEntityFromUuid(UUID id){for(Entity e:loadedEntityList)if(e.getUniqueID().equals(id))return e;return null;}
        @Override public java.util.Calendar getCurrentDate(){return new GregorianCalendar(2026,0,1);}
        @Override public EnumDifficulty getDifficulty(){return EnumDifficulty.NORMAL;}
        @Override public void notifyBlockUpdate(BlockPos p,IBlockState before,IBlockState after,int flags){}
    }
    static WorldFixture world()throws Exception {
        WorldFixture w=(WorldFixture)TrialPlacementSmoke.unsafe.allocateInstance(WorldFixture.class);
        w.blocks=new HashMap<>();w.tiles=new HashMap<>();w.storage=new MapStorage(null);w.clock=100;
        w.info=new WorldInfo(new WorldSettings(1,GameType.SURVIVAL,true,false,WorldType.DEFAULT),"temporal-fixture");
        BloodthirstSmoke.field(w,World.class,"worldInfo",w.info);
        BloodthirstSmoke.field(w,World.class,"provider",new WorldProviderSurface());
        BloodthirstSmoke.field(w,World.class,"profiler",new net.minecraft.profiler.Profiler());
        BloodthirstSmoke.field(w,World.class,"rand",new Random(7));
        BloodthirstSmoke.field(w,World.class,"loadedEntityList",new ArrayList<>());
        BloodthirstSmoke.field(w,World.class,"loadedTileEntityList",new ArrayList<>());
        BloodthirstSmoke.field(w,World.class,"playerEntities",new ArrayList<>());
        w.providerFixture=(Provider)TrialPlacementSmoke.unsafe.allocateInstance(Provider.class);
        w.providerFixture.chunks=Collections.singletonList(new Chunk(w,0,0));
        ServerFixture server=(ServerFixture)TrialPlacementSmoke.unsafe.allocateInstance(ServerFixture.class);server.worlds=new WorldServer[]{w};w.server=server;
        Class<?> queue=Class.forName("net.minecraft.world.WorldServer$ServerBlockEventList");
        Object queues=java.lang.reflect.Array.newInstance(queue,2);
        java.lang.reflect.Constructor<?> constructor=queue.getDeclaredConstructor();constructor.setAccessible(true);
        for(int i=0;i<2;i++)java.lang.reflect.Array.set(queues,i,constructor.newInstance());
        BloodthirstSmoke.field(w,WorldServer.class,"blockEventQueue",queues);
        return w;
    }
    public static void main(String[] args)throws Exception {
        TrialPlacementSmoke.main(args);
        check(AbilityType.MORPHISM.ordinal()==17&&AbilityDiscoveryItem.IDS[23].equals("body_regen"),"existing enum and scroll metadata remain stable");
        EncounterWaveSmoke.Player player=(EncounterWaveSmoke.Player)TrialPlacementSmoke.unsafe.allocateInstance(EncounterWaveSmoke.Player.class);
        player.data=new NBTTagCompound();
        AbilityStorage.setLevel(player,AbilityType.TIME_REWIND,3);
        check(!TimeBranches.unlocked(player,AbilityType.TIME_REWIND),"child branch cannot bypass unpurchased parent");
        AbilityStorage.setLevel(player,AbilityType.SLOW,1);
        check(TimeBranches.unlocked(player,AbilityType.TIME_REWIND)&&!TimeBranches.unlocked(player,AbilityType.TIME_FUTURE),"rewind and future unlock independently");
        for(int level=1;level<=3;level++){
            check(TimeBranches.rewindTicks(level)==new int[]{100,200,400}[level-1],"rewind depth improves with purchased level");
            check(TimeBranches.futureTicks(level)==new int[]{100,200,300}[level-1],"future duration improves with purchased level");
        }
        NBTTagCompound past=new NBTTagCompound(),present=new NBTTagCompound();
        past.setDouble("pocketmod_mana",1000);past.setDouble(TimeBranches.PAID,10);present.setDouble(TimeBranches.PAID,110);
        NBTTagCompound restored=TimeBranches.restoreCost(past,present);
        check(restored.getDouble("pocketmod_mana")==900&&restored.getDouble(TimeBranches.PAID)==110,"rewind cannot refund one hundred mana of temporal payments");
        check(TimeBranches.restoreCost(past,restored).equals(restored),"restoring the same frame does not charge twice");
        check(past.getDouble("pocketmod_mana")==1000,"recorded frame is immutable");
        WorldFixture w=world();player.world=w;
        EntityCow cow=new EntityCow(w);cow.setPosition(0,65,0);w.loadedEntityList.add(cow);
        BlockPos block=new BlockPos(1,65,1),chestPos=new BlockPos(2,65,2);
        w.blocks.put(block,Blocks.STONE.getDefaultState());w.blocks.put(chestPos,Blocks.CHEST.getDefaultState());
        TileEntityChest chest=new TileEntityChest();chest.setWorld(w);chest.setPos(chestPos);w.tiles.put(chestPos,chest);w.loadedTileEntityList.add(chest);
        TemporalSystem.Track track=new TemporalSystem.Track(w);track.owner=player.getUniqueID();track.mode=TemporalSystem.FUTURE;
        TemporalSystem.tracks.put(w,track);
        check(TemporalSystem.capture(track),"checkpoint captured");track.checkpoint=track.history.current();
        for(int tick=1;tick<=4;tick++){
            w.clock++;TemporalSystem.beforeBlock(w,block);w.setBlockState(block,(tick%2==0?Blocks.GOLD_BLOCK:Blocks.DIAMOND_BLOCK).getDefaultState(),2);
            cow.setPosition(tick,65,0);cow.setHealth(10-tick);cow.ticksExisted=100+tick;cow.limbSwing=tick*2;chest.setInventorySlotContents(0,new ItemStack(Items.DIAMOND,tick));
            check(TemporalSystem.capture(track),"future tick captured");
        }
        java.util.List<TemporalSnapshot> recorded=new ArrayList<>();for(int i=0;i<track.history.size();i++)recorded.add(track.history.at(i));
        TemporalSystem.preview(player);
        check(track.mode==TemporalSystem.RETURN&&cow.posX==4&&chest.getStackInSlot(0).getCount()==4,"second press starts return without an instant reset");
        check(TemporalSystem.reverseFrame(track)&&cow.posX==3&&cow.getHealth()==7&&cow.ticksExisted==103&&cow.limbSwing==6&&chest.getStackInSlot(0).getCount()==3,"one rewind tick restores mob state and chest items together");
        check(w.getBlockState(block).getBlock()==Blocks.DIAMOND_BLOCK,"block delta restored in matching frame");
        check(TemporalSystem.reverseFrame(track)&&cow.posX==2&&chest.getStackInSlot(0).getCount()==2,"next tick visits intermediate future");
        TemporalSystem.preview(player);
        check(track.mode==TemporalSystem.IDLE&&cow.posX==2&&chest.getStackInSlot(0).getCount()==2,"third press commits the selected intermediate tick");
        check(track.history.size()==0&&!TemporalSystem.frozen(w),"discarded future cannot replay or keep world frozen");
        TemporalSystem.Track complete=new TemporalSystem.Track(w);complete.owner=player.getUniqueID();complete.mode=TemporalSystem.RETURN;complete.budget=4;
        for(TemporalSnapshot frame:recorded)complete.history.add(frame);
        TemporalSystem.tracks.put(w,complete);
        for(int i=0;i<4;i++)check(TemporalSystem.reverseFrame(complete),"automatic return walks every tick to checkpoint");
        check(cow.posX==0&&chest.getStackInSlot(0).isEmpty()&&w.getBlockState(block).getBlock()==Blocks.STONE,"complete return restores checkpoint blocks, items and mob");
        TemporalSystem.preview(player);
        TemporalSystem.Track rewind=new TemporalSystem.Track(w);rewind.owner=player.getUniqueID();rewind.mode=TemporalSystem.REWIND;rewind.budget=100;rewind.branchLevel=1;
        rewind.history.add(recorded.get(0));rewind.history.add(recorded.get(1));rewind.history.add(recorded.get(2));
        w.playerEntities.add(player);Mana.set(player,100);
        check(TemporalSystem.reverseFrame(rewind)&&Mana.get(player)==96,"rewind branch spends four mana per recorded tick at level one");
        int cursor=rewind.history.position();Mana.set(player,0);
        check(!TemporalSystem.reverseFrame(rewind)&&rewind.history.position()==cursor,"insufficient mana cannot move the timeline cursor");
        w.playerEntities.clear();TemporalSystem.tracks.remove(w);
        System.out.println("TemporalBranchesSmoke: "+checks+" branch, cost, snapshot, gradual return and interruption checks passed; no game launched");
    }
}
