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
        CommandNode<CommandSourceStack> render = warmod.getChild("render");
        assertNotNull(render);
        assertNotNull(render.getChild("quality"));
        assertNull(render.getChild("budget"));
        if (SharedConstants.IS_RUNNING_IN_IDE) assertNotNull(render.getChild("diagnose"));
        else assertNull(render.getChild("diagnose"));
        assertNull(warmod.getChild("renderer"));
        assertNull(dispatcher.getRoot().getChild("war_mod"));
    }
}
