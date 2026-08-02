package com.andye.warmod.icbm.client;

import com.andye.warmod.icbm.IcbmConstants;
import com.andye.warmod.icbm.network.ClientboundIcbmLaunchPayload;
import com.andye.warmod.icbm.network.ClientboundIcbmGuidanceUpdatePayload;
import com.andye.warmod.icbm.network.ClientboundIcbmRemovePayload;
import com.andye.warmod.icbm.network.ClientboundIcbmSeparationPayload;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

public final class ClientIcbmVisualManager {
	public static final ClientIcbmVisualManager INSTANCE=new ClientIcbmVisualManager();private final Map<UUID,IcbmVisualState> activeMissiles=new LinkedHashMap<>();private final Map<UUID,SpentIcbmStageState> spentStages=new LinkedHashMap<>();private ClientLevel activeLevel;private ClientIcbmVisualManager(){}
	public synchronized void acceptLaunch(final ClientboundIcbmLaunchPayload p){if(!p.isWellFormed()||!ensure(Minecraft.getInstance().level))return;activeMissiles.remove(p.missileId());trim(activeMissiles,IcbmConstants.MAX_ACTIVE_CLIENT_ICBMS);activeMissiles.put(p.missileId(),IcbmVisualState.fromPayload(p));}
	public synchronized void acceptGuidance(final ClientboundIcbmGuidanceUpdatePayload p){if(!p.isWellFormed()||!ensure(Minecraft.getInstance().level))return;activeMissiles.computeIfPresent(p.missileId(),(id,state)->state.withGuidance(p));}
	public synchronized void acceptSeparation(final ClientboundIcbmSeparationPayload p){if(!p.isWellFormed()||!ensure(Minecraft.getInstance().level))return;activeMissiles.remove(p.missileId());spentStages.remove(p.missileId());trim(spentStages,IcbmConstants.MAX_ACTIVE_SPENT_STAGES);spentStages.put(p.missileId(),SpentIcbmStageState.from(p));IcbmSeparationEffect.spawn(activeLevel,p);}
	public synchronized void acceptRemove(final ClientboundIcbmRemovePayload p){if(p.isWellFormed()&&ensure(Minecraft.getInstance().level))activeMissiles.remove(p.missileId());}
	public synchronized void tick(final Minecraft client){if(!ensure(client.level))return;long time=client.level.getGameTime();activeMissiles.entrySet().removeIf(e->e.getValue().expired(time));spentStages.entrySet().removeIf(e->e.getValue().expired(time));}
	public synchronized Snapshot snapshot(final ClientLevel level){return level!=null&&level==activeLevel?new Snapshot(List.copyOf(activeMissiles.values()),List.copyOf(spentStages.values())):Snapshot.EMPTY;}
	public synchronized void clear(){activeMissiles.clear();spentStages.clear();activeLevel=null;}
	private boolean ensure(final ClientLevel level){if(level==null){clear();return false;}if(level!=activeLevel){clear();activeLevel=level;}return true;}private static <T> void trim(final Map<UUID,T> map,final int limit){while(map.size()>=limit){Iterator<UUID> it=map.keySet().iterator();if(!it.hasNext())return;it.next();it.remove();}}
	public record Snapshot(List<IcbmVisualState> missiles,List<SpentIcbmStageState> spentStages){private static final Snapshot EMPTY=new Snapshot(List.of(),List.of());}
}
