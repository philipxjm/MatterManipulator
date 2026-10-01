package com.recursive_pineapple.matter_manipulator.client.gui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import com.recursive_pineapple.matter_manipulator.MMMod;
import com.recursive_pineapple.matter_manipulator.common.building.schematic.SchematicData;
import com.recursive_pineapple.matter_manipulator.common.building.schematic.SchematicImport;
import com.recursive_pineapple.matter_manipulator.common.building.schematic.SchematicUploads;
import com.recursive_pineapple.matter_manipulator.common.networking.SchematicChannel;

/**
 * Picks a .schematic from the game's schematics folder (Schematica's) and imports it into the held manipulator as
 * the copy source: the file is kept in the client store and sent to the server in chunks.
 */
@SideOnly(Side.CLIENT)
public class GuiImportSchematic extends GuiScreen {

    private static final int BUTTON_IMPORT = 0, BUTTON_CANCEL = 1, BUTTON_UP = 2;

    private final File root;
    private File dir;
    private final List<File> entries = new ArrayList<>();
    private int selected = -1;
    private EntrySlot slot;
    private GuiButton importButton, upButton;
    private String status = "";

    public GuiImportSchematic() {
        this.root = new File(Minecraft.getMinecraft().mcDataDir, "schematics");
        this.dir = this.root;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.importButton = new GuiButton(
            BUTTON_IMPORT,
            this.width / 2 - 154,
            this.height - 28,
            100,
            20,
            StatCollector.translateToLocal("mm.gui.import_schematic.import")
        );
        this.upButton = new GuiButton(
            BUTTON_UP,
            this.width / 2 - 50,
            this.height - 28,
            100,
            20,
            StatCollector.translateToLocal("mm.gui.import_schematic.up")
        );
        this.buttonList.add(this.importButton);
        this.buttonList.add(this.upButton);
        this.buttonList.add(
            new GuiButton(
                BUTTON_CANCEL,
                this.width / 2 + 54,
                this.height - 28,
                100,
                20,
                StatCollector.translateToLocal("gui.cancel")
            )
        );
        this.slot = new EntrySlot();
        this.slot.registerScrollButtons(7, 8);
        refresh();
    }

    private void refresh() {
        this.entries.clear();
        this.selected = -1;
        this.dir.mkdirs();
        final File[] files = this.dir.listFiles(
            f -> f.isDirectory() || f.getName()
                .toLowerCase()
                .endsWith(".schematic")
        );
        if (files != null) {
            Arrays.sort(
                files,
                Comparator.comparing((File f) -> !f.isDirectory())
                    .thenComparing(
                        f -> f.getName()
                            .toLowerCase()
                    )
            );
            this.entries.addAll(Arrays.asList(files));
        }
        this.upButton.enabled = !this.dir.equals(this.root);
        updateButtons();
    }

    private void updateButtons() {
        this.importButton.enabled = this.selected >= 0 && this.entries.get(this.selected)
            .isFile();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case BUTTON_IMPORT -> {
                if (this.selected >= 0) open(this.entries.get(this.selected));
            }
            case BUTTON_UP -> {
                if (!this.dir.equals(this.root)) {
                    this.dir = this.dir.getParentFile();
                    refresh();
                }
            }
            case BUTTON_CANCEL -> this.mc.displayGuiScreen(null);
            default -> this.slot.actionPerformed(button);
        }
    }

    private void open(File file) {
        if (file.isDirectory()) {
            this.dir = file;
            refresh();
            return;
        }

        try {
            final byte[] bytes = Files.readAllBytes(file.toPath());
            if (bytes.length > SchematicImport.MAX_FILE_BYTES) {
                this.status = StatCollector.translateToLocalFormatted("mm.info.error.schematic_too_big", file.getName());
                return;
            }
            final SchematicData schematic = SchematicData.parse(bytes);
            if ((long) schematic.width * schematic.height * schematic.length > SchematicImport.MAX_VOLUME) {
                this.status = StatCollector.translateToLocalFormatted("mm.info.error.schematic_too_big", file.getName());
                return;
            }

            final String sha1 = SchematicImport.sha1(bytes);
            // The client keeps its own copy to draw the paste preview from.
            SchematicImport.store(true, sha1, bytes);

            final String name = relativeName(file);
            for (int offset = 0; offset < bytes.length; offset += SchematicUploads.CHUNK_BYTES) {
                final byte[] chunk = Arrays
                    .copyOfRange(bytes, offset, Math.min(bytes.length, offset + SchematicUploads.CHUNK_BYTES));
                SchematicChannel.NET.sendToServer(new SchematicChannel.Chunk(sha1, name, bytes.length, offset, chunk));
            }

            this.mc.displayGuiScreen(null);
        } catch (IOException e) {
            MMMod.LOG.error("Schematic import: could not read " + file, e);
            this.status = StatCollector.translateToLocalFormatted("mm.info.error.schematic_unreadable", file.getName(), e.getMessage());
            if (this.mc.thePlayer != null) {
                this.mc.thePlayer.addChatMessage(
                    new ChatComponentTranslation("mm.info.error.schematic_unreadable", file.getName(), e.getMessage())
                        .setChatStyle(new net.minecraft.util.ChatStyle().setColor(EnumChatFormatting.RED))
                );
            }
        }
    }

    private String relativeName(File file) {
        final String path = this.root.toURI()
            .relativize(file.toURI())
            .getPath();
        return path.isEmpty() ? file.getName() : path;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.slot.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(this.fontRendererObj, StatCollector.translateToLocal("mm.gui.import_schematic.title"), this.width / 2, 8, 0xFFFFFF);
        drawCenteredString(
            this.fontRendererObj,
            relativeName(this.dir).isEmpty() ? "schematics/" : "schematics/" + relativeName(this.dir),
            this.width / 2,
            20,
            0xA0A0A0
        );
        if (!this.status.isEmpty()) {
            drawCenteredString(this.fontRendererObj, this.status, this.width / 2, this.height - 42, 0xFF6060);
        } else if (this.entries.isEmpty()) {
            drawCenteredString(
                this.fontRendererObj,
                StatCollector.translateToLocal("mm.gui.import_schematic.empty"),
                this.width / 2,
                this.height / 2,
                0xA0A0A0
            );
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private class EntrySlot extends GuiSlot {

        EntrySlot() {
            super(GuiImportSchematic.this.mc, GuiImportSchematic.this.width, GuiImportSchematic.this.height, 32, GuiImportSchematic.this.height - 48, 14);
        }

        @Override
        protected int getSize() {
            return entries.size();
        }

        @Override
        protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
            selected = index;
            status = "";
            updateButtons();
            if (doubleClick) open(entries.get(index));
        }

        @Override
        protected boolean isSelected(int index) {
            return index == selected;
        }

        @Override
        protected void drawBackground() {
            drawDefaultBackground();
        }

        @Override
        protected void drawSlot(int index, int x, int y, int height, Tessellator tessellator, int mouseX, int mouseY) {
            final File file = entries.get(index);
            final String label = file.isDirectory() ? EnumChatFormatting.YELLOW + file.getName() + "/" : file.getName();
            fontRendererObj.drawString(label, x + 2, y + 1, 0xFFFFFF);
            if (file.isFile()) {
                final String size = String.format("%.1f KB", file.length() / 1024.0);
                fontRendererObj.drawString(size, x + 216 - fontRendererObj.getStringWidth(size), y + 1, 0x808080);
            }
        }
    }
}
