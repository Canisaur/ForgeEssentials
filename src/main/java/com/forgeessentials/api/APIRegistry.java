package com.forgeessentials.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.forgeessentials.api.UserIdent.NpcUserIdent;
import com.forgeessentials.api.UserIdent.ServerUserIdent;
import com.forgeessentials.api.economy.Economy;
import com.forgeessentials.api.permissions.IPermissionsHelper;
import com.forgeessentials.api.remote.FERemoteHandler;
import com.forgeessentials.api.remote.RemoteHandler;
import com.forgeessentials.api.remote.RemoteManager;

/**
 * This is the central access point for all FE API functions
 */
public class APIRegistry
{

    /**
     * Use this to call API functions available in the economy module.
     */
    public static Economy economy;

    /**
     * Use to call API functions from the permissions module.
     */
    public static IPermissionsHelper perms;

    /**
     * Use to call scripting API functions, or to invoke a script run from outside the module.
     */
    public static ScriptHandler scripts;

    /**
     * This manager allows registering custom {@link RemoteHandler}s for remote-module. Please be careful to use unique
     * IDs when registering handlers.
     * 
     * Using this instance to register handlers is deprecated. Use the {@link FERemoteHandler} annotation instead.
     */
    @Deprecated
    public static RemoteManager remoteManager = new RemoteManager.DefaultRemoteHandlerManager();

    /**
     * Use this annotation to mark classes where static methods with other FE annotations might be.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE })
    public @interface ForgeEssentialsRegistrar
    {
        String ident();
    }

}
