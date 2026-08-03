package com.andye.warmod.phalanx.client.gui;

import com.andye.warmod.menu.PhalanxMenu;
import com.andye.warmod.phalanx.PhalanxConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public final class PhalanxScreen extends AbstractContainerScreen<PhalanxMenu> {
    public PhalanxScreen(PhalanxMenu menu, Inventory inventory, Component title) { super(menu, inventory, title, 224, 188); }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partial){ graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff20262a); for(Slot slot:menu.slots){int x=leftPos+slot.x-1,y=topPos+slot.y-1;graphics.fill(x,y,x+18,y+18,0xff68757a);graphics.fill(x+1,y+1,x+17,y+17,0xff0b1114);} super.extractRenderState(graphics,mouseX,mouseY,partial); var turret=menu.turret(); graphics.text(font,Component.literal("PHALANX POINT DEFENCE"),leftPos+8,topPos+8,0xffe0b34a); if(turret==null)return; double spread=PhalanxConstants.BASE_SPREAD_DEGREES+turret.bloom(); int y=topPos+26; graphics.text(font,Component.literal("Status: "+turret.status().name().replace('_',' ')),leftPos+8,y,0xff8fd5b5); graphics.text(font,Component.literal("Enabled: "+(turret.enabled()?"YES":"NO")),leftPos+8,y+12,0xffb9c1c5); graphics.text(font,Component.literal("Ammunition: "+turret.rounds()+" / 128"),leftPos+8,y+24,0xffb9c1c5); graphics.text(font,Component.literal("Engagement range: 192 blocks"),leftPos+8,y+54,0xffb9c1c5); graphics.text(font,Component.literal("Protected impact radius: 100 blocks"),leftPos+8,y+66,0xffb9c1c5); graphics.text(font,Component.literal("Elevation limit: -5° to +60°"),leftPos+8,y+78,0xffb9c1c5); graphics.text(font,Component.literal(String.format(java.util.Locale.ROOT,"Current aim spread: %.2f°",spread)),leftPos+8,y+90,0xffb9c1c5); graphics.text(font,Component.literal("Spread increases during sustained fire"),leftPos+8,y+106,0xff899499); }
}