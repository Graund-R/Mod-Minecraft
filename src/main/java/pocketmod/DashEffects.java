package pocketmod;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** One server-approved visual cue for the caster and nearby viewers. Never authorizes movement. */
public final class DashEffects {
    private DashEffects(){}
    public static void start(EntityLivingBase actor,int sequence,int duration) {
        if(actor.world.isRemote)return;
        send(actor,new Message(actor,sequence,Math.max(1,Math.min(100,duration)),false));
        if(PocketConfig.dashSounds){
            actor.world.playSound(null,actor.getPosition(),DashSounds.SNAP,actor instanceof net.minecraft.entity.player.EntityPlayer?SoundCategory.PLAYERS:SoundCategory.HOSTILE,.6F,1);
            actor.world.playSound(null,actor.getPosition(),DashSounds.RUSH,actor instanceof net.minecraft.entity.player.EntityPlayer?SoundCategory.PLAYERS:SoundCategory.HOSTILE,.75F,1);
        }
    }
    public static void end(EntityLivingBase actor,int sequence) {
        if(actor.world.isRemote)return;
        send(actor,new Message(actor,sequence,0,true));
        if(PocketConfig.dashSounds)actor.world.playSound(null,actor.getPosition(),DashSounds.LAND,actor instanceof net.minecraft.entity.player.EntityPlayer?SoundCategory.PLAYERS:SoundCategory.HOSTILE,.65F,1);
    }
    public static void cancel(EntityLivingBase actor,int sequence){if(!actor.world.isRemote)send(actor,new Message(actor,sequence,-1,true));}
    private static void send(EntityLivingBase actor,Message message) {
        NetworkHandler.CHANNEL_INSTANCE.sendToAllAround(message,new NetworkRegistry.TargetPoint(actor.dimension,actor.posX,actor.posY,actor.posZ,96));
    }
    public static final class Message implements IMessage {
        public int dimension,entityId,sequence,duration;public boolean end;
        public Message(){}
        Message(EntityLivingBase actor,int sequence,int duration,boolean end){dimension=actor.dimension;entityId=actor.getEntityId();this.sequence=sequence;this.duration=duration;this.end=end;}
        @Override public void fromBytes(ByteBuf b){dimension=b.readInt();entityId=b.readInt();sequence=b.readInt();duration=b.readInt();end=b.readBoolean();}
        @Override public void toBytes(ByteBuf b){b.writeInt(dimension);b.writeInt(entityId);b.writeInt(sequence);b.writeInt(duration);b.writeBoolean(end);}
    }
    /** Common proxy registers this inert handler on dedicated servers; client code stays isolated. */
    public static final class Ignore implements IMessageHandler<Message,IMessage> {
        public IMessage onMessage(Message message,MessageContext context){return null;}
    }
}
