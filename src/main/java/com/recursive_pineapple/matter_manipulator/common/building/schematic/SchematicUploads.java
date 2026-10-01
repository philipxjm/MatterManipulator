package com.recursive_pineapple.matter_manipulator.common.building.schematic;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import com.recursive_pineapple.matter_manipulator.MMMod;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState.PlaceMode;

/**
 * Server side of importing: client-to-server packets are capped at 32 KiB, so the client sends the file in chunks;
 * this reassembles them per player, checks and stores the file, and sets it as the held manipulator's copy source.
 */
public final class SchematicUploads {

    /** Bytes of file data per packet (packet limit 32767, minus the header). */
    public static final int CHUNK_BYTES = 24 * 1024;

    private static final class Upload {

        final String sha1, name;
        final byte[] data;
        int received;

        Upload(String sha1, String name, int total) {
            this.sha1 = sha1;
            this.name = name;
            this.data = new byte[total];
        }
    }

    private static final Map<UUID, Upload> UPLOADS = new ConcurrentHashMap<>();

    // Plain text, not translation keys: players with the stock mod have no translations for these.
    static void say(EntityPlayer player, EnumChatFormatting color, String text) {
        player.addChatComponentMessage(new ChatComponentText(text).setChatStyle(new ChatStyle().setColor(color)));
    }

    static void error(EntityPlayer player, String text) {
        say(player, EnumChatFormatting.RED, text);
    }

    private SchematicUploads() {}

    public static void receive(EntityPlayer player, String sha1, String name, int total, int offset, byte[] chunk) {
        final UUID id = player.getUniqueID();
        if (total <= 0 || total > SchematicImport.MAX_FILE_BYTES) {
            UPLOADS.remove(id);
            error(player, name + " is too big to import.");
            return;
        }

        Upload upload = UPLOADS.get(id);
        if (offset == 0 || upload == null || !upload.sha1.equals(sha1) || upload.data.length != total) {
            if (offset != 0) return; // stray chunk of an abandoned upload
            upload = new Upload(sha1, name, total);
            UPLOADS.put(id, upload);
        }
        if (offset != upload.received || offset + chunk.length > total) {
            UPLOADS.remove(id);
            error(player, "Importing " + name + " failed; try again.");
            return;
        }
        System.arraycopy(chunk, 0, upload.data, offset, chunk.length);
        upload.received += chunk.length;
        if (upload.received < total) return;

        UPLOADS.remove(id);
        finish(player, upload);
    }

    private static void finish(EntityPlayer player, Upload upload) {
        if (
            !SchematicImport.sha1(upload.data)
                .equals(upload.sha1)
        ) {
            error(player, "Importing " + upload.name + " failed; try again.");
            return;
        }
        apply(player, upload.name, upload.data);
    }

    /**
     * Checks a schematic file, stores it on the server and makes it the copy source of the manipulator the player
     * holds. Used by uploads from a client with this fork and by the /mmschematic command.
     */
    public static boolean apply(EntityPlayer player, String name, byte[] data) {
        final String sha1 = SchematicImport.sha1(data);

        final SchematicData schematic;
        try {
            schematic = SchematicData.parse(data);
        } catch (IOException e) {
            error(player, "Could not read " + name + ": " + e.getMessage());
            return false;
        }
        if ((long) schematic.width * schematic.height * schematic.length > SchematicImport.MAX_VOLUME) {
            error(player, name + " is too big to import.");
            return false;
        }

        try {
            SchematicImport.store(false, sha1, data);
        } catch (IOException e) {
            MMMod.LOG.error("Schematic import: could not store " + name, e);
            error(player, "Importing " + name + " failed; try again.");
            return false;
        }

        final ItemStack held = player.inventory.getCurrentItem();
        if (held == null || !(held.getItem() instanceof ItemMatterManipulator)) {
            error(player, "Hold the Matter Manipulator while importing a schematic.");
            return false;
        }
        final MMState state = ItemMatterManipulator.getState(held);
        if (!state.hasCap(ItemMatterManipulator.ALLOW_COPYING)) {
            error(player, "This Matter Manipulator cannot copy, so it cannot import schematics.");
            return false;
        }

        state.config.importedSchematic = new ImportedSchematic(
            name,
            sha1,
            schematic.width,
            schematic.height,
            schematic.length
        );
        state.config.placeMode = PlaceMode.COPYING;
        state.config.action = null;
        ItemMatterManipulator.setState(held, state);

        say(
            player,
            EnumChatFormatting.GRAY,
            "Imported " + name + " (" + schematic.width + "x" + schematic.height + "x" + schematic.length + "). Mark a paste location to place it."
        );
        if (!schematic.unknownBlocks.isEmpty()) {
            say(
                player,
                EnumChatFormatting.GOLD,
                schematic.unknownBlocks.values()
                    .stream()
                    .mapToInt(Integer::intValue)
                    .sum() + " blocks in the schematic are not in this game and will be skipped: "
                    + String.join(", ", schematic.unknownBlocks.keySet())
            );
        }
        return true;
    }
}
