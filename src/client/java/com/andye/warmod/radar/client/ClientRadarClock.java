package com.andye.warmod.radar.client;
import net.minecraft.client.Minecraft;
public final class ClientRadarClock{private long serverTime,clientTime;public void synchronize(long server){Minecraft mc=Minecraft.getInstance();serverTime=server;clientTime=mc.level==null?0:mc.level.getGameTime();}public double now(float partial){Minecraft mc=Minecraft.getInstance();return serverTime+(mc.level==null?0:mc.level.getGameTime()-clientTime)+partial;}}
