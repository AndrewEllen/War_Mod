package com.andye.warmod;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

final class WarModCommandTest {
    @Test
    void registersOneWarmodRenderControlTree() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

        WarModCommand.register(dispatcher);

        CommandNode<CommandSourceStack> warmod = dispatcher.getRoot().getChild("warmod");
        assertNotNull(warmod);
        assertNotNull(warmod.getChild("render"));
        assertNull(warmod.getChild("renderer"));
        assertNull(dispatcher.getRoot().getChild("war_mod"));
    }
}
