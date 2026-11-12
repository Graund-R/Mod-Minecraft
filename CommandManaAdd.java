package pocketmod;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/** /manaadd [число] — добавить ману (для тестов, работает и без читов). */
public class CommandManaAdd extends CommandBase {

    @Override
    public String getName() {
        return "manaadd";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/manaadd [amount]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP p = getCommandSenderAsPlayer(sender);
        double amt = 1000.0;
        if (args.length > 0) {
            try {
                amt = Double.parseDouble(args[0]);
            } catch (NumberFormatException e) {
                throw new WrongUsageException(getUsage(sender));
            }
        }
        Mana.add(p, amt);
        sender.sendMessage(new TextComponentString("Mana: " + (long) Mana.get(p)));
    }
}
