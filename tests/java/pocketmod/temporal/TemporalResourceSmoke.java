package pocketmod;
import net.minecraft.nbt.*;
/** Rewinding nested inventories must not refill or clone the active dial's charge. */
public final class TemporalResourceSmoke {
    public static void main(String[] args){
        TemporalSystem.Track track=new TemporalSystem.Track(null);track.dialId="active";
        track.dial.setString("dial_id","active");track.dial.setInteger("rewind",38);track.dial.setInteger("charges",1);
        NBTTagCompound chest=new NBTTagCompound();NBTTagList items=new NBTTagList();
        items.appendTag(item("active",400));items.appendTag(item("spare",200));chest.setTag("Items",items);
        NBTTagCompound restored=chest.copy();TemporalSnapshot.patchItems(restored,track);
        NBTTagList result=restored.getTagList("Items",10);
        if(result.getCompoundTagAt(0).getCompoundTag("tag").getInteger("rewind")!=38)throw new AssertionError("active charge restored by rewind");
        if(result.getCompoundTagAt(1).getCompoundTag("tag").getInteger("rewind")!=200)throw new AssertionError("spare dial changed");
        if(chest.getTagList("Items",10).getCompoundTagAt(0).getCompoundTag("tag").getInteger("rewind")!=400)throw new AssertionError("snapshot was mutated");
        track.dial.setInteger("rewind",36);TemporalSnapshot.patchItems(restored,track);
        if(result.getCompoundTagAt(0).getCompoundTag("tag").getInteger("rewind")!=36)throw new AssertionError("redo refunded charge");
        System.out.println("Temporal charges: nested inventory and redo accounting passed");
    }
    private static NBTTagCompound item(String id,int charge){
        NBTTagCompound n=new NBTTagCompound();n.setString("id","pocketmod:temporal_dial");NBTTagCompound tag=new NBTTagCompound();tag.setString("dial_id",id);tag.setInteger("rewind",charge);n.setTag("tag",tag);return n;
    }
}
