package pocketmod;

import java.util.*;
import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

/** Emergency release remains available to operators even if the session owner is unavailable. */
public final class CommandStasis extends CommandBase {
    public String getName(){return "stasis";}
    public String getUsage(ICommandSender s){return "/stasis break | start | future | rewind";}
    public int getRequiredPermissionLevel(){return 0;}
    public void execute(MinecraftServer server,ICommandSender sender,String[] args)throws CommandException {
        if(args.length==0)throw new WrongUsageException(getUsage(sender));
        if("break".equals(args[0])) {
            if(sender.canUseCommand(2,"stasis"))TemporalSystem.emergency(sender.getEntityWorld());
            else TemporalSystem.action(getCommandSenderAsPlayer(sender),2,0);
            return;
        }
        EntityPlayerMP p=getCommandSenderAsPlayer(sender);
        if("start".equals(args[0])){Abilities.toggleSlow(p);return;}
        if("preview".equals(args[0])||"future".equals(args[0])){TemporalSystem.preview(p);return;}
        if("rewind".equals(args[0])){TemporalSystem.action(p,3,args.length>1?parseInt(args[1],1,10):5);return;}
        throw new WrongUsageException(getUsage(sender));
    }
    public List<String> getTabCompletions(MinecraftServer server,ICommandSender sender,String[] args,BlockPos target){return args.length==1?getListOfStringsMatchingLastWord(args,"break","start","future","rewind"):Collections.emptyList();}
}
