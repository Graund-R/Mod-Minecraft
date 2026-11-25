package pocketmod;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Endpoints only: never adds a cast delay, moves the player, spends mana or authorizes a jump. */
public final class TeleportEffects {
    private TeleportEffects(){}
    public static void depart(EntityLivingBase actor,WorldServer world,Vec3d center){send(actor,world,center,false);}
    public static void arrive(EntityLivingBase actor,WorldServer world,Vec3d center){send(actor,world,center,true);}
    private static void send(EntityLivingBase actor,WorldServer world,Vec3d center,boolean arrival){
        if(!TeleportFold.valid(center.x,center.y,center.z))return;
        Message message=new Message();message.dimension=world.provider.getDimension();message.actorId=actor.getEntityId();message.x=center.x;message.y=center.y;message.z=center.z;
        message.width=Math.max(.3F,Math.min(4,actor.width));message.height=Math.max(1,Math.min(5,actor.height));message.arrival=arrival;message.hostile=!(actor instanceof net.minecraft.entity.player.EntityPlayer);
        NetworkHandler.CHANNEL_INSTANCE.sendToAllAround(message,new NetworkRegistry.TargetPoint(message.dimension,center.x,center.y,center.z,96));
    }
    public static final class Message implements IMessage {
        public int dimension,actorId;public double x,y,z;public float width,height;public boolean arrival,hostile;
        public Message(){}
        @Override public void fromBytes(ByteBuf b){dimension=b.readInt();actorId=b.readInt();x=b.readDouble();y=b.readDouble();z=b.readDouble();width=b.readFloat();height=b.readFloat();arrival=b.readBoolean();hostile=b.readBoolean();}
        @Override public void toBytes(ByteBuf b){b.writeInt(dimension);b.writeInt(actorId);b.writeDouble(x);b.writeDouble(y);b.writeDouble(z);b.writeFloat(width);b.writeFloat(height);b.writeBoolean(arrival);b.writeBoolean(hostile);}
    }
    public static final class Ignore implements IMessageHandler<Message,IMessage>{public IMessage onMessage(Message message,MessageContext context){return null;}}
}
