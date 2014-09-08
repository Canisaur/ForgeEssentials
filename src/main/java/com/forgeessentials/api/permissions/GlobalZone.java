package com.forgeessentials.api.permissions;

import com.forgeessentials.api.APIRegistry;
import com.forgeessentials.util.selections.WorldArea;
import com.forgeessentials.util.selections.WorldPoint;

import net.minecraft.entity.player.EntityPlayer;

public class GlobalZone extends Zone {

	public GlobalZone()
	{
		super(1);
	}
	
	@Override
	public boolean isPointInZone(WorldPoint point)
	{
		return true;
	}

	@Override
	public boolean isAreaInZone(WorldArea point)
	{
		return true;
	}

	@Override
	public boolean isPartOfAreaInZone(WorldArea point)
	{
		return true;
	}

	@Override
	public Zone getParent()
	{
		return APIRegistry.perms.getRootZone();
	}

	@Override
	public String getName()
	{
		return "GLOBAL";
	}

}
