package com.forgeessentials.tickets;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.permissions.PermissionsManager;
import net.minecraftforge.permissions.PermissionsManager.RegisteredPermValue;

import com.forgeessentials.core.ForgeEssentials;
import com.forgeessentials.core.moduleLauncher.FEModule;
import com.forgeessentials.data.api.ClassContainer;
import com.forgeessentials.data.api.DataStorageManager;
import com.forgeessentials.util.ChatUtils;
import com.forgeessentials.util.events.modules.FEModuleInitEvent;
import com.forgeessentials.util.events.modules.FEModuleServerInitEvent;
import com.forgeessentials.util.events.modules.FEModuleServerStopEvent;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

@FEModule(name = "Tickets", parentMod = ForgeEssentials.class, configClass = ConfigTickets.class)
public class ModuleTickets {
    public static final String PERMBASE = "fe.tickets";
    @FEModule.Config
    public static ConfigTickets config;
    @FEModule.ModuleDir
    public static File moduleDir;
    public static ArrayList<Ticket> ticketList = new ArrayList<Ticket>();
    public static List<String> categories = new ArrayList<String>();

    public static int currentID;

    private static ClassContainer ticketContainer = new ClassContainer(Ticket.class);

    @FEModule.Init
    public void load(FEModuleInitEvent e)
    {
        FMLCommonHandler.instance().bus().register(this);
    }

    @FEModule.ServerInit
    public void serverStarting(FEModuleServerInitEvent e)
    {
        e.registerServerCommand(new Command());
        loadAll();
        PermissionsManager.registerPermission(PERMBASE + ".new", RegisteredPermValue.TRUE);
        PermissionsManager.registerPermission(PERMBASE + ".view", RegisteredPermValue.TRUE);

        PermissionsManager.registerPermission(PERMBASE + ".tp", RegisteredPermValue.TRUE);
        PermissionsManager.registerPermission(PERMBASE + ".admin", RegisteredPermValue.OP);
    }

    @FEModule.ServerStop
    public void serverStopping(FEModuleServerStopEvent e)
    {
        saveAll();
        config.forceSave();
    }

    /**
     * Used to get ID for new Tickets
     *
     * @return
     */
    public static int getNextID()
    {
        currentID++;
        return currentID;
    }

    public static void loadAll()
    {
        for (Object obj : DataStorageManager.getReccomendedDriver().loadAllObjects(ticketContainer))
        {
            ticketList.add((Ticket) obj);
        }
    }

    public static void saveAll()
    {
        for (Ticket ticket : ticketList)
        {
            DataStorageManager.getReccomendedDriver().saveObject(ticketContainer, ticket);
        }
    }

    public static Ticket getID(int i)
    {
        for (Ticket ticket : ticketList)
        {
            if (ticket.id == i)
            {
                return ticket;
            }
        }
        return null;
    }
    
    @SubscribeEvent
     public void loadData(PlayerEvent.PlayerLoggedInEvent e)
    {
        if (PermissionsManager.checkPermission(e.player, ModuleTickets.PERMBASE + ".admin"))
        {
            if (!ModuleTickets.ticketList.isEmpty())
            {
                ChatUtils.sendMessage(e.player, EnumChatFormatting.DARK_AQUA + "There are " + ModuleTickets.ticketList.size() + " open tickets.");
            }
        }
    }
}
