package com.forgeessentials.backup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.permissions.PermissionsManager.RegisteredPermValue;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import com.forgeessentials.api.APIRegistry;
import com.forgeessentials.api.UserIdent;
import com.forgeessentials.core.ForgeEssentials;
import com.forgeessentials.core.misc.TaskRegistry;
import com.forgeessentials.core.moduleLauncher.FEModule;
import com.forgeessentials.core.moduleLauncher.config.ConfigLoader.ConfigLoaderBase;
import com.forgeessentials.util.OutputHandler;
import com.forgeessentials.util.ServerUtil;
import com.forgeessentials.util.events.FEModuleEvent.FEModuleInitEvent;
import com.forgeessentials.util.events.FEModuleEvent.FEModuleServerInitEvent;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

@FEModule(name = "Backups", parentMod = ForgeEssentials.class)
public class ModuleBackup extends ConfigLoaderBase
{

    public static final String PERM = "fe.backup";
    public static final String PERM_NOTIFY = PERM + ".notify";
    public static final String PERM_NOTIFY_SAVE = PERM_NOTIFY + ".save";
    public static final String PERM_NOTIFY_BACKUP = PERM_NOTIFY + ".backup";

    public static final String CONFIG_CAT = "Backup";
    public static final String CONFIG_CAT_WORLDS = CONFIG_CAT + ".Worlds";
    public static final String WORLDS_HELP = "Add world configurations in the format \"B:1=true\"";

    public static final SimpleDateFormat FILE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm");

    /* ------------------------------------------------------------ */

    public static boolean backupDefault;

    public static int backupInterval;

    public static boolean backupOnUnload;

    public static boolean backupOnLoad;

    public static int keepBackups;

    public static int dailyBackups;

    public static int weeklyBackups;

    public static Map<Integer, Boolean> backupOverrides = new HashMap<>();

    private static Runnable backupTask = new Runnable() {
        @Override
        public void run()
        {
            backupAll();
        }
    };

    private static Thread backupThread;

    /* ------------------------------------------------------------ */

    @FEModule.ModuleDir
    public static File moduleDir;

    public static File baseFolder;

    /* ------------------------------------------------------------ */

    @SubscribeEvent
    public void load(FEModuleInitEvent e)
    {
        // Register configuration
        ForgeEssentials.getConfigManager().registerLoader(CONFIG_CAT, this);
        // FECommandManager.registerCommand(new CommandBackup());

        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void serverStarting(FEModuleServerInitEvent e)
    {
        APIRegistry.perms.registerPermission(PERM_NOTIFY_SAVE, RegisteredPermValue.OP);
        APIRegistry.perms.registerPermission(PERM_NOTIFY_BACKUP, RegisteredPermValue.OP);
        registerBackupTask();
        cleanBackups();
    }

    private void registerBackupTask()
    {
        TaskRegistry.getInstance().remove(backupTask);
        if (backupInterval > 0)
            TaskRegistry.getInstance().scheduleRepeated(backupTask, 1000 * 60 * backupInterval);
    }

    @SubscribeEvent
    public void worldLoadEvent(WorldEvent.Load event)
    {
        if (!FMLCommonHandler.instance().getEffectiveSide().isServer() || !backupOnLoad)
            return;
        final WorldServer world = (WorldServer) event.world;
        if (shouldBackup(world))
        {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run()
                {
                    backup(world);
                }
            });
            thread.start();
        }
    }

    @SubscribeEvent
    public void worldUnloadEvent(WorldEvent.Unload event)
    {
        if (!FMLCommonHandler.instance().getEffectiveSide().isServer() || !backupOnUnload)
            return;
        final WorldServer world = (WorldServer) event.world;
        if (shouldBackup(world))
        {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run()
                {
                    backup(world);
                }
            });
            thread.start();
        }
    }

    @Override
    public void load(Configuration config, boolean isReload)
    {
        backupDefault = config.get(CONFIG_CAT, "backup_default", true, "Backup all worlds by default").getBoolean();
        backupInterval = config.get(CONFIG_CAT, "backup_interval", 1, "Automatic backup interval in minutes (0 to disable)").getInt();
        backupOnLoad = config.get(CONFIG_CAT, "backup_on_load", true, "Always backup worlds when loaded (server starts)").getBoolean();
        backupOnUnload = config.get(CONFIG_CAT, "backup_on_unload", true, "Always backup when a world is unloaded").getBoolean();
        keepBackups = config.get(CONFIG_CAT, "keep_backups", 10, "Keep at least this amount of last backups").getInt();
        dailyBackups = config.get(CONFIG_CAT, "keep_daily_backups", 7, "Keep at least one daily backup for this last number of last days").getInt();
        weeklyBackups = config.get(CONFIG_CAT, "keep_weekly_backups", 8, "Keep at least one weekly backup for this last number of weeks").getInt();

        config.get(CONFIG_CAT_WORLDS, "0", true).getBoolean(); // Create default entry
        ConfigCategory worldCat = config.getCategory(CONFIG_CAT_WORLDS);
        worldCat.setComment(WORLDS_HELP);
        for (Entry<String, Property> world : worldCat.entrySet())
        {
            try
            {
                if (world.getValue().isBooleanValue())
                    backupOverrides.put(Integer.parseInt(world.getKey()), world.getValue().getBoolean());
            }
            catch (NumberFormatException e)
            {
                OutputHandler.felog.severe("Invalid backup override entry!");
            }
        }

        if (MinecraftServer.getServer() != null && MinecraftServer.getServer().isServerRunning())
            registerBackupTask();
    }

    /* ------------------------------------------------------------ */

    public static void backupAll()
    {
        if (backupThread != null)
            return;
        backupThread = new Thread(new Runnable() {
            @Override
            public void run()
            {
                try
                {
                    for (WorldServer world : DimensionManager.getWorlds())
                        if (shouldBackup(world))
                            backup(world);
                    cleanBackups();
                }
                finally
                {
                    backupThread = null;
                }
            }
        });
        backupThread.start();
    }

    protected static boolean shouldBackup(WorldServer world)
    {
        Boolean shouldBackup = backupOverrides.get(world.provider.dimensionId);
        if (shouldBackup == null)
            return backupDefault;
        else
            return shouldBackup;
    }

    private static synchronized void backup(WorldServer world)
    {
        notify(String.format("Starting backup of dim %d...", world.provider.dimensionId));

        // Save world
        if (!saveWorld(world))
        {
            notify("Backup failed: Could not save world");
            return;
        }

        // Prepare directory
        URI baseUri = ServerUtil.getWorldPath().toURI();
        File backupFile = getBackupFile(world);
        File backupDir = backupFile.getParentFile();
        if (!backupDir.exists())
            if (!backupDir.mkdirs())
            {
                notify("Backup failed: Could not create backup directory");
                return;
            }

        // Save files
        try (FileOutputStream fileStream = new FileOutputStream(backupFile); //
                ZipOutputStream zipStream = new ZipOutputStream(fileStream);)
        {
            for (File file : enumWorldFiles(world, world.getChunkSaveLocation(), null))
            {
                String fileName = baseUri.relativize(file.toURI()).getPath();

                ZipEntry ze = new ZipEntry(fileName);
                zipStream.putNextEntry(ze);
                try (FileInputStream in = new FileInputStream(file))
                {
                    IOUtils.copy(in, zipStream);
                }
            }
            zipStream.closeEntry();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }

        notify("Backup finished");
    }

    private static List<File> enumWorldFiles(WorldServer world, File dir, List<File> files)
    {
        if (files == null)
            files = new ArrayList<>();
        mainLoop: for (File file : dir.listFiles())
        {
            // Exclude directories of other worlds
            for (WorldServer otherWorld : DimensionManager.getWorlds())
                if (otherWorld != world && otherWorld.getChunkSaveLocation().equals(file))
                    continue mainLoop;

            if (file.isDirectory())
                enumWorldFiles(world, file, files);
            else
                files.add(file);
        }
        return files;
    }

    private static File getBackupFile(WorldServer world)
    {
        return new File(moduleDir, String.format("%s/DIM_%d/%s.zip", //
                world.getWorldInfo().getWorldName(), //
                world.provider.dimensionId, //
                FILE_FORMAT.format(new Date())));
    }

    private static boolean saveWorld(WorldServer world)
    {
        boolean oldLevelSaving = world.levelSaving;
        world.levelSaving = false;
        try
        {
            world.saveAllChunks(true, (IProgressUpdate) null);
            return true;
        }
        catch (MinecraftException e)
        {
            OutputHandler.felog.severe(String.format("Could not save world %d", world.provider.dimensionId));
            return false;
        }
        finally
        {
            world.levelSaving = oldLevelSaving;
        }
    }

    private static void cleanBackups()
    {
        File baseDir = new File(moduleDir, DimensionManager.getWorld(0).getWorldInfo().getWorldName());
        for (File backupDir : baseDir.listFiles())
        {
            if (!backupDir.isDirectory())
                continue;
            SortedMap<Calendar, File> files = new TreeMap<>();
            for (File backupFile : backupDir.listFiles())
            {
                try
                {
                    Calendar date = Calendar.getInstance();
                    date.setTime(FILE_FORMAT.parse(FilenameUtils.getBaseName(backupFile.getName())));
                    files.put(date, backupFile);
                }
                catch (ParseException e)
                {
                    OutputHandler.felog.severe(String.format("Could not parse backup file %s", backupFile.getAbsolutePath()));
                }
            }

            Calendar now = Calendar.getInstance();

            Calendar oldestDailyBackup = Calendar.getInstance();
            oldestDailyBackup.set(Calendar.MILLISECOND, 0);
            oldestDailyBackup.set(Calendar.SECOND, 0);
            oldestDailyBackup.set(Calendar.HOUR_OF_DAY, 4);
            oldestDailyBackup.add(Calendar.DAY_OF_YEAR, dailyBackups <= 0 ? -1000 : -dailyBackups);

            Calendar oldestWeeklyBackup = Calendar.getInstance();
            oldestDailyBackup.set(Calendar.MILLISECOND, 0);
            oldestDailyBackup.set(Calendar.SECOND, 0);
            oldestDailyBackup.set(Calendar.HOUR_OF_DAY, 4);
            oldestWeeklyBackup.set(Calendar.DAY_OF_WEEK, 0);
            oldestWeeklyBackup.add(Calendar.WEEK_OF_YEAR, weeklyBackups <= 0 ? -1000 : -weeklyBackups);

            Calendar oldestBackup = oldestDailyBackup.before(oldestWeeklyBackup) ? oldestDailyBackup : oldestWeeklyBackup;

            int index = 0;
            for (Iterator<Entry<Calendar, File>> it = files.entrySet().iterator(); it.hasNext();)
            {
                Entry<Calendar, File> backup = it.next();
                if (index++ > files.size() - keepBackups)
                {
                    it.remove();
                }
                else if (backup.getKey().before(oldestBackup))
                {
                    if (!backup.getValue().delete())
                        OutputHandler.felog.severe(String.format("Could not delete backup file %s", backup.getValue().getAbsolutePath()));
                    it.remove();
                }
            }

            while (oldestDailyBackup.before(now))
            {
                Calendar nextDate = (Calendar) oldestDailyBackup.clone();
                nextDate.add(Calendar.DAY_OF_YEAR, 1);
                boolean first = true;
                for (Iterator<Entry<Calendar, File>> it = files.entrySet().iterator(); it.hasNext();)
                {
                    Entry<Calendar, File> backup = it.next();
                    if (backup.getKey().before(oldestDailyBackup))
                        continue;
                    if (first)
                    {
                        first = false;
                        continue;
                    }
                    if (backup.getKey().after(nextDate))
                        break;
                    if (!backup.getValue().delete())
                        OutputHandler.felog.severe(String.format("Could not delete backup file %s", backup.getValue().getAbsolutePath()));
                    it.remove();
                }
                oldestDailyBackup = nextDate;
            }

            while (oldestWeeklyBackup.before(now))
            {
                Calendar nextDate = (Calendar) oldestWeeklyBackup.clone();
                nextDate.add(Calendar.WEEK_OF_YEAR, 1);
                boolean first = true;
                for (Iterator<Entry<Calendar, File>> it = files.entrySet().iterator(); it.hasNext();)
                {
                    Entry<Calendar, File> backup = it.next();
                    if (backup.getKey().before(oldestWeeklyBackup))
                        continue;
                    if (first)
                    {
                        first = false;
                        continue;
                    }
                    if (backup.getKey().after(nextDate))
                        break;
                    if (!backup.getValue().delete())
                        OutputHandler.felog.severe(String.format("Could not delete backup file %s", backup.getValue().getAbsolutePath()));
                    it.remove();
                }
                oldestWeeklyBackup = nextDate;
            }
        }
    }

    private static void notify(String message)
    {
        IChatComponent messageComponent = OutputHandler.notification(message);
        if (!MinecraftServer.getServer().isServerStopped())
            for (EntityPlayerMP player : ServerUtil.getPlayerList())
                if (UserIdent.get(player).checkPermission(PERM_NOTIFY_BACKUP))
                    OutputHandler.sendMessage(player, messageComponent);
        OutputHandler.sendMessage(MinecraftServer.getServer(), messageComponent);
    }

}
