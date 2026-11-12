package pocketmod;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/**
 * Тестовые команды:
 *  /pocketdebug unlock      — все способности на максимум
 *  /pocketdebug expire      — кредит просрочен прямо сейчас
 *  /pocketdebug mult <x>    — задать множитель цен
 */
public class CommandPocketDebug extends CommandBase {

    @Override
    public String getName() {
        return "pocketdebug";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/pocketdebug <unlock|expire|mult> [value]";
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
        if (args.length == 0) {
            throw new WrongUsageException(getUsage(sender));
        }
        if ("unlock".equals(args[0])) {
            for (AbilityType t : AbilityType.values()) {
                AbilityStorage.setLevel(p, t, t.maxLevel);
            }
            sender.sendMessage(new TextComponentString("All abilities maxed"));
        } else if ("expire".equals(args[0])) {
            if (!Mana.hasCredit(p)) {
                sender.sendMessage(new TextComponentString("No credit"));
            } else {
                Mana.expireCreditNow(p);
                sender.sendMessage(new TextComponentString("Credit expires on next tick"));
            }
        } else if ("mult".equals(args[0]) && args.length > 1) {
            try {
                Mana.setPriceMultiplier(p, Double.parseDouble(args[1]));
                sender.sendMessage(new TextComponentString("Multiplier: " + Mana.getPriceMultiplier(p)));
            } catch (NumberFormatException e) {
                throw new WrongUsageException(getUsage(sender));
            }
        } else {
            throw new WrongUsageException(getUsage(sender));
        }
    }
}
