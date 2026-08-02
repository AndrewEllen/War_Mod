package com.andye.warmod.silo.client.gui;

import com.andye.warmod.block.MissileSiloGuidanceFrameStructure;
import com.andye.warmod.block.MissileSiloState;
import com.andye.warmod.block.entity.MissileSiloBlockEntity;
import com.andye.warmod.menu.MissileSiloMenu;
import com.andye.warmod.silo.MissilePayloadItems;
import com.andye.warmod.silo.network.*;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class MissileSiloScreen extends AbstractContainerScreen<MissileSiloMenu> {
    private EditBox xField,yField,zField;private Button launchButton;
    public MissileSiloScreen(MissileSiloMenu menu,Inventory inventory,Component title){super(menu,inventory,title,286,196);this.titleLabelX=8;this.titleLabelY=6;this.inventoryLabelX=27;this.inventoryLabelY=101;}
    @Override protected void init(){super.init();int x=leftPos+187,y=topPos+28;xField=field(x,y,"X");yField=field(x,y+22,"Y");zField=field(x,y+44,"Z");MissileSiloBlockEntity silo=menu.silo();if(silo!=null&&silo.storedTarget()!=null){var p=silo.storedTarget().position();xField.setValue(format(p.x));yField.setValue(format(p.y));zField.setValue(format(p.z));}addRenderableWidget(Button.builder(Component.literal("Apply Target"),b->apply()).bounds(x,y+66,88,20).build());addRenderableWidget(Button.builder(Component.literal("Clear"),b->send(new ServerboundSiloClearTargetPayload(menu.containerId,menu.centre(),menu.siloId()))).bounds(x,y+88,42,20).build());addRenderableWidget(Button.builder(Component.literal("From Held"),b->send(new ServerboundSiloUseHeldDesignatorPayload(menu.containerId,menu.centre(),menu.siloId()))).bounds(x+46,y+88,42,20).build());launchButton=addRenderableWidget(Button.builder(Component.literal("LAUNCH"),b->send(new ServerboundSiloLaunchPayload(menu.containerId,menu.centre(),menu.siloId()))).bounds(x,y+112,88,20).build());}
    private EditBox field(int x,int y,String hint){EditBox box=new EditBox(font,x,y,88,18,Component.literal(hint));box.setHint(Component.literal(hint));box.setMaxLength(20);addRenderableWidget(box);return box;}
    private void apply(){try{double x=Double.parseDouble(xField.getValue()),y=Double.parseDouble(yField.getValue()),z=Double.parseDouble(zField.getValue());send(new ServerboundSiloSetTargetPayload(menu.containerId,menu.centre(),menu.siloId(),x,y,z));}catch(NumberFormatException ignored){}}
    private static void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload){if(ClientPlayNetworking.canSend(payload.type()))ClientPlayNetworking.send(payload);}
    @Override protected void containerTick(){super.containerTick();MissileSiloBlockEntity silo=menu.silo();launchButton.active=silo!=null&&silo.siloState()==MissileSiloState.READY;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick){g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff11181c);g.fill(leftPos+8,topPos+18,leftPos+70,topPos+94,0xff1b272d);g.fill(leftPos+75,topPos+18,leftPos+180,topPos+94,0xff172126);g.fill(leftPos+183,topPos+18,leftPos+278,topPos+174,0xff172126);super.extractRenderState(g,mouseX,mouseY,partialTick);MissileSiloBlockEntity silo=menu.silo();if(silo==null)return;String payload=MissilePayloadItems.payloadType(silo.missileStack()).map(t->t.serializedName()).orElse("empty");g.text(font,Component.literal("MISSILE SILO  "+menu.siloId().toString().substring(0,8)),leftPos+8,topPos+6,0xffffc45a);g.text(font,Component.literal("Payload: "+payload),leftPos+10,topPos+67,0xffd5e3e8);g.text(font,Component.literal("Count: "+silo.missileStack().getCount()+" / 16"),leftPos+10,topPos+79,0xffd5e3e8);g.text(font,Component.literal("State: "+silo.siloState()),leftPos+80,topPos+26,0xffffffff);int tier=silo.installedGuidanceTier();g.text(font,Component.literal("Guidance tier: "+tier),leftPos+80,topPos+42,0xffd5e3e8);g.text(font,Component.literal("Max error: ±"+(int)MissileSiloGuidanceFrameStructure.maximumGuidanceError(tier)+" X/Z"),leftPos+80,topPos+56,0xffd5e3e8);double reload=silo.reloadTicksTotal()==0?0:1.0-(double)silo.reloadTicksRemaining()/silo.reloadTicksTotal();g.fill(leftPos+80,topPos+72,leftPos+170,topPos+80,0xff26383f);g.fill(leftPos+80,topPos+72,leftPos+80+(int)(90*reload),topPos+80,0xffffb43b);String target=silo.storedTarget()==null?"Target: not set":String.format(Locale.ROOT,"Target: %.0f %.0f %.0f",silo.storedTarget().position().x,silo.storedTarget().position().y,silo.storedTarget().position().z);g.text(font,Component.literal(target),leftPos+80,topPos+84,0xffa9bdc5);}
    private static String format(double v){return String.format(Locale.ROOT,"%.1f",v);}
}