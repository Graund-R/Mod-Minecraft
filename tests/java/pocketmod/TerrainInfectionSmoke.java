package pocketmod;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.BlockRenderLayer;

/** Persistent spatial infection and renderer batching, without a client/server. */
public final class TerrainInfectionSmoke {
    static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    static class Delegate extends ChunkRenderContainer {
        List<RenderChunk> rendered=new ArrayList<>();int batches;
        @Override public void renderChunkLayer(BlockRenderLayer layer){rendered.addAll(renderChunks);renderChunks.clear();batches++;}
    }
    static class Pass implements ExileGrayClient.TerrainPass {
        List<Integer> colors=new ArrayList<>();boolean enabled=true;int ended;
        public boolean begin(){return enabled;}
        public void color(int style){colors.add(style);}
        public void end(){ended++;}
    }
    public static void main(String[] args)throws Exception {
        TrialPlacementSmoke.main(args);
        ExileWorldData data=new ExileWorldData("test");data.seconds=1000;
        data.setExposure(0,-1,0,ExileConfig.fullSeconds);
        check(data.blackPercent(0,-1,0)==0,"full infection initially gray, not black");
        data.seconds+=59;check(data.blackPercent(0,-1,0)==0,"first minute grace");
        data.seconds++;check(data.blackPercent(0,-1,0)==20,"first minute is twenty percent darker");
        data.seconds+=240;check(data.blackPercent(0,-1,0)==100,"five minutes reaches exact black");
        check(data.spreadRate(0,-1,0)==1.5,"spread accelerates sixfold by black stage");
        NBTTagCompound map=data.terrain(0,0,0,34);NBTTagList chunks=map.getTagList("chunks",10);
        check(chunks.tagCount()==1&&chunks.getCompoundTagAt(0).getInteger("x")==-1,"snapshot shows infected neighbor from clean chunk");
        check(chunks.getCompoundTagAt(0).getInteger("black")==100,"snapshot carries local blackness");
        check(data.terrain(1,0,0,34).getTagList("chunks",10).tagCount()==0,"dimensions never share coloration");
        check(data.terrain(0,100,100,34).getTagList("chunks",10).tagCount()==0,"snapshot bounded by visible neighborhood");
        ExileWorldData restored=new ExileWorldData("test");restored.readFromNBT(data.writeToNBT(new NBTTagCompound()));
        check(restored.blackPercent(0,-1,0)==100,"black clock survives save/reload");
        data.seconds+=7200;
        check(data.blackPercent(0,-1,0)==100,"walking away never resets black clock");
        data.setExposure(0,-1,0,0);check(data.blackPercent(0,-1,0)==0,"explicit cleaning resets black state");
        ExileWorldData spread=new ExileWorldData("spread");spread.setExposure(0,0,0,ExileConfig.fullSeconds);
        spread.seconds=60;spread.spread(0,(x,z)->x>=0&&x<=1&&z==0);
        check(spread.exposure(0,1,0)==.5,"one-minute source infects loaded neighbor at increased speed");
        check(spread.exposure(0,-1,0)==0,"unloaded neighbors are not generated or infected");
        check(spread.exposure(0,2,0)==0,"spread does not cascade several chunks in one tick");
        spread.setExposure(0,0,1,ExileConfig.fullSeconds);spread.ageFull(0,0,1,5);
        spread.seconds++;spread.spread(0,(x,z)->x>=0&&x<=1&&z>=0&&z<=1);
        check(spread.exposure(0,1,0)==2.0,"overlapping sources use largest contribution, never sum");
        spread.setExposure(0,0,0,ExileConfig.fullSeconds);spread.ageFull(0,0,0,5);
        check(spread.blackPercent(0,0,0)==100,"F8 aging works even near world time zero");
        NBTTagCompound legacy=restored.writeToNBT(new NBTTagCompound());
        NBTTagList old=legacy.getTagList("areas",10);old.getCompoundTagAt(0).removeTag("full");old.getCompoundTagAt(0).removeTag("fullSince");
        ExileWorldData migrated=new ExileWorldData("legacy");migrated.readFromNBT(legacy);
        check(migrated.blackPercent(0,-1,0)==0,"legacy full chunks start a fresh blackening clock");
        check(ExileGrayClient.key(-1,0)!=ExileGrayClient.key(0,-1)&&ExileGrayClient.key(-1,-1)!=ExileGrayClient.key(1,1),"signed chunk coordinate keys stay distinct");
        Delegate delegate=new Delegate();Pass pass=new Pass();Map<RenderChunk,Integer> styles=new IdentityHashMap<>();
        ExileGrayClient.ShadedChunks wrapper=new ExileGrayClient.ShadedChunks(delegate,c->styles.get(c),pass);
        List<RenderChunk> order=new ArrayList<>();
        wrapper.initialize(1,2,3);
        for(int style:new int[]{0,0,25600,25600,25700,0}) {
            RenderChunk chunk=(RenderChunk)TrialPlacementSmoke.unsafe.allocateInstance(RenderChunk.class);
            styles.put(chunk,style);order.add(chunk);wrapper.addRenderChunk(chunk,BlockRenderLayer.TRANSLUCENT);
        }
        wrapper.renderChunkLayer(BlockRenderLayer.TRANSLUCENT);
        check(delegate.rendered.equals(order),"transparent water order preserved exactly");
        check(delegate.batches==4&&pass.colors.equals(Arrays.asList(0,25600,25700,0)),"consecutive styles batched, clean chunks leave shader");
        check(pass.ended==1,"original program restored after terrain layer");
        delegate.rendered.clear();pass.enabled=false;wrapper.initialize(0,0,0);
        for(RenderChunk chunk:order)wrapper.addRenderChunk(chunk,BlockRenderLayer.SOLID);
        wrapper.renderChunkLayer(BlockRenderLayer.SOLID);
        check(delegate.rendered.equals(order)&&delegate.batches==5,"unsupported/disabled shader falls back to entire original terrain renderer");
        System.out.println("TerrainInfectionSmoke: "+checks+" checks passed; no game launched");
    }
}
