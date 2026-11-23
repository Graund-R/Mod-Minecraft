package pocketmod;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;

/** Доступная игроку справка. Команда не меняет выбранный при создании мира режим. */
public final class CommandExile extends CommandBase {
    @Override
    public String getName() {
        return "exile";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/exile";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        EntityPlayerMP p = getCommandSenderAsPlayer(sender);
        if (!ExileMode.enabled(p.world)) {
            p.sendMessage(new TextComponentTranslation("pocketmod.exile.normal_world"));
            return;
        }
        NetworkHandler.sendPrompt(p, 2, 0, 0);
    }
}
