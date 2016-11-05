package com.forgeessentials.util.selections;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.permission.PermissionLevel;

import com.forgeessentials.util.ChatUtil;
import com.forgeessentials.util.ForgeEssentialsCommandBase;
import com.forgeessentials.util.PlayerInfo;

public class CommandDeselect extends ForgeEssentialsCommandBase
{

    @Override
    public String getCommandName()
    {
        return "/fedesel";
    }

    @Override
    public String[] getDefaultAliases()
    {
        return new String[] { "/fedeselect", "/deselect", "/sel" };
    }

    @Override
    public void processCommandPlayer(EntityPlayerMP sender, String[] args)
    {
        PlayerInfo info = PlayerInfo.get(sender.getPersistentID());
        info.setSel1(null);
        info.setSel2(null);
        SelectionHandler.sendUpdate(sender);
        ChatUtil.chatConfirmation(sender, "Selection cleared.");
    }

    @Override
    public boolean canConsoleUseCommand()
    {
        return false;
    }

    @Override
    public String getPermissionNode()
    {
        return "fe.core.pos.deselect";
    }

    @Override
    public String getCommandUsage(ICommandSender sender)
    {

        return "//fedesel Deselects the selection";
    }

    @Override
    public PermissionLevel getPermissionLevel()
    {

        return PermissionLevel.TRUE;
    }
}
