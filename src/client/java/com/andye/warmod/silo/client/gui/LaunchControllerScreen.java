package com.andye.warmod.silo.client.gui;

import com.andye.warmod.block.entity.MissileSiloBlockEntity;
import com.andye.warmod.client.gui.WarModUiText;
import com.andye.warmod.item.component.LinkedSilo;
import com.andye.warmod.menu.LaunchControllerMenu;
import com.andye.warmod.silo.network.ServerboundLaunchControllerLaunchPayload;
import com.andye.warmod.silo.network.ServerboundLaunchControllerRemoveSiloPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class LaunchControllerScreen
    extends AbstractContainerScreen<LaunchControllerMenu> {

    private static final int SCREEN_WIDTH = 360;
    private static final int SCREEN_HEIGHT = 232;
    private static final int LINKS_PER_PAGE = 5;
    private static final int LIST_X = 10;
    private static final int LIST_Y = 74;
    private static final int LIST_WIDTH = 340;
    private static final int ROW_HEIGHT = 24;

    private final List<Button> removeButtons = new ArrayList<>();
    private Button launchButton;
    private Button previousButton;
    private Button nextButton;
    private int page;
    private List<UUID> visibleLinkIds = List.of();

    public LaunchControllerScreen(
        final LaunchControllerMenu menu,
        final Inventory inventory,
        final Component title
    ) {
        super(menu, inventory, title, SCREEN_WIDTH, SCREEN_HEIGHT);
        titleLabelX = -10_000;
        inventoryLabelX = -10_000;
    }

    @Override
    protected void init() {
        super.init();
        removeButtons.clear();

        List<LinkedSilo> links = menu.linkedSilos();
        int pageCount = pageCount(links.size());
        page = Math.min(page, pageCount - 1);
        int start = page * LINKS_PER_PAGE;
        int end = Math.min(links.size(), start + LINKS_PER_PAGE);
        for (int index = start; index < end; index++) {
            LinkedSilo link = links.get(index);
            Button remove = addRenderableWidget(Button.builder(
                Component.literal("Remove"),
                ignored -> remove(link)
            ).bounds(
                leftPos + LIST_X + LIST_WIDTH - 66,
                topPos + LIST_Y + (index - start) * ROW_HEIGHT + 2,
                62,
                20
            ).build());
            removeButtons.add(remove);
        }

        launchButton = addRenderableWidget(Button.builder(
            Component.literal("Launch all (saved targets)"),
            ignored -> launchAll()
        ).bounds(leftPos + 10, topPos + 202, 190, 22).build());
        previousButton = addRenderableWidget(Button.builder(
            Component.literal("<"), ignored -> changePage(-1)
        ).bounds(leftPos + 236, topPos + 202, 38, 22).build());
        nextButton = addRenderableWidget(Button.builder(
            Component.literal(">"), ignored -> changePage(1)
        ).bounds(leftPos + 312, topPos + 202, 38, 22).build());
        visibleLinkIds = linkIds(links);
        refreshButtons(links);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        List<LinkedSilo> links = menu.linkedSilos();
        List<UUID> currentIds = linkIds(links);
        if (!currentIds.equals(visibleLinkIds)) {
            resize(width, height);
            return;
        }
        refreshButtons(links);
    }

    private void refreshButtons(final List<LinkedSilo> links) {
        int pages = pageCount(links.size());
        page = Math.min(page, pages - 1);
        launchButton.active = !links.isEmpty();
        previousButton.active = page > 0;
        nextButton.active = page + 1 < pages;
    }

    private void launchAll() {
        ClientPlayNetworking.send(new ServerboundLaunchControllerLaunchPayload(
            menu.containerId,
            menu.centre(),
            menu.controllerId()
        ));
    }

    private void remove(final LinkedSilo link) {
        ClientPlayNetworking.send(new ServerboundLaunchControllerRemoveSiloPayload(
            menu.containerId,
            menu.centre(),
            menu.controllerId(),
            link.siloId()
        ));
    }

    private void changePage(final int delta) {
        page = Math.max(0, Math.min(pageCount(menu.linkedSilos().size()) - 1, page + delta));
        resize(width, height);
    }

    @Override
    public void extractRenderState(
        final GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final float partialTick
    ) {
        WarModUiText.frame(graphics, leftPos, topPos, imageWidth, imageHeight);
        WarModUiText.section(graphics, leftPos + LIST_X, topPos + LIST_Y,
            LIST_WIDTH, LINKS_PER_PAGE * ROW_HEIGHT);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        WarModUiText.text(graphics, font, Component.literal("LAUNCH CONTROLLER"),
            leftPos + 10, topPos + 9, WarModUiText.ACCENT);
        WarModUiText.text(graphics, font,
            WarModUiText.ellipsize(font, Component.literal(
                "Linking Tool: use this controller once, then use each silo to add it."), 340),
            leftPos + 10, topPos + 27, WarModUiText.TEXT);
        WarModUiText.text(graphics, font,
            WarModUiText.ellipsize(font, Component.literal(
                "Remote: sneak-use a silo, then this controller; clear it to bind the group."), 340),
            leftPos + 10, topPos + 39, WarModUiText.TEXT_MUTED);

        List<LinkedSilo> links = menu.linkedSilos();
        String count = links.size() + " / 64 silos";
        WarModUiText.text(graphics, font, Component.literal(count),
            leftPos + imageWidth - 10 - font.width(count), topPos + 57,
            links.isEmpty() ? WarModUiText.TEXT_MUTED : WarModUiText.SUCCESS);
        WarModUiText.text(graphics, font,
            WarModUiText.ellipsize(font, Component.literal(menu.lastBatchSummary()), 245),
            leftPos + 10, topPos + 57, WarModUiText.TEXT_MUTED);

        int start = page * LINKS_PER_PAGE;
        int end = Math.min(links.size(), start + LINKS_PER_PAGE);
        if (links.isEmpty()) {
            WarModUiText.text(graphics, font, Component.literal("No silos linked"),
                leftPos + 20, topPos + LIST_Y + 12, WarModUiText.TEXT_MUTED);
        }
        for (int index = start; index < end; index++) {
            drawLink(graphics, links.get(index), index, index - start);
        }

        String pages = (page + 1) + " / " + pageCount(links.size());
        WarModUiText.text(graphics, font, Component.literal(pages),
            leftPos + 293 - font.width(pages) / 2, topPos + 209,
            WarModUiText.TEXT_MUTED);
    }

    private void drawLink(
        final GuiGraphicsExtractor graphics,
        final LinkedSilo link,
        final int index,
        final int row
    ) {
        int x = leftPos + LIST_X + 7;
        int y = topPos + LIST_Y + row * ROW_HEIGHT;
        String identity = "Silo " + (index + 1) + "  #" + shortId(link.siloId());
        WarModUiText.text(graphics, font, Component.literal(identity), x, y + 3,
            WarModUiText.TEXT);

        String location = link.centre().getX() + ", " + link.centre().getY() + ", "
            + link.centre().getZ() + "  " + dimensionName(link);
        WarModUiText.text(graphics, font,
            WarModUiText.ellipsize(font, Component.literal(location), 190),
            x, y + 13, WarModUiText.TEXT_MUTED);

        Status status = status(link);
        WarModUiText.text(graphics, font, Component.literal(status.label()),
            leftPos + LIST_X + 211, y + 8, status.colour());
    }

    private Status status(final LinkedSilo link) {
        if (minecraft.level == null) return new Status("UNKNOWN", WarModUiText.TEXT_MUTED);
        if (!minecraft.level.dimension().equals(link.dimension()))
            return new Status("OTHER WORLD", WarModUiText.TEXT_MUTED);
        if (!minecraft.level.hasChunkAt(link.centre()))
            return new Status("UNLOADED", WarModUiText.WARNING);
        if (!(minecraft.level.getBlockEntity(link.centre()) instanceof MissileSiloBlockEntity silo)
            || !silo.siloId().equals(link.siloId()))
            return new Status("STALE", WarModUiText.ERROR);
        String state = switch (silo.siloState()) {
            case NO_TARGET -> "NO TARGET";
            case PREPARING -> "ARMING";
            case LAUNCHING -> "LAUNCH";
            case COOLDOWN -> "COOLING";
            case RELOADING -> "RELOAD";
            case INVALID_STRUCTURE -> "INVALID";
            default -> silo.siloState().name().toUpperCase(Locale.ROOT);
        };
        int colour = state.equals("READY") ? WarModUiText.SUCCESS : WarModUiText.WARNING;
        return new Status(WarModUiText.ellipsize(font, state, 57), colour);
    }

    private static String dimensionName(final LinkedSilo link) {
        String path = link.dimension().identifier().getPath();
        return path.replace('_', ' ');
    }

    private static String shortId(final UUID id) {
        return id.toString().substring(0, 8);
    }

    private static int pageCount(final int links) {
        return Math.max(1, (links + LINKS_PER_PAGE - 1) / LINKS_PER_PAGE);
    }

    private static List<UUID> linkIds(final List<LinkedSilo> links) {
        return links.stream().map(LinkedSilo::siloId).toList();
    }

    private record Status(String label, int colour) { }
}
