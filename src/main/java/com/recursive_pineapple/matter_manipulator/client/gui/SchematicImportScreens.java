package com.recursive_pineapple.matter_manipulator.client.gui;

import net.minecraft.client.Minecraft;

import com.recursive_pineapple.matter_manipulator.client.ClientTasks;

/**
 * Client-only entry point to the schematic import screen. Common classes (ItemMatterManipulator is loaded on the
 * dedicated server too) must only call this static method: creating the screen there directly makes the JVM verifier
 * load GuiScreen, which the server refuses ("Attempted to load class ... for invalid side SERVER").
 */
public final class SchematicImportScreens {

    private SchematicImportScreens() {}

    /** Opens the file list on the next tick, after the radial menu that asked for it has closed. */
    public static void openImport() {
        ClientTasks.nextTick(
            () -> Minecraft.getMinecraft()
                .displayGuiScreen(new GuiImportSchematic())
        );
    }
}
