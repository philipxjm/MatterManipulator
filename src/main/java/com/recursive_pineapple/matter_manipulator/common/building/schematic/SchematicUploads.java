package com.recursive_pineapple.matter_manipulator.common.building.schematic;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.recursive_pineapple.matter_manipulator.MMMod;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState.PlaceMode;
import com.recursive_pineapple.matter_manipulator.common.utils.MMUtils;

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

    private SchematicUploads() {}

    public static void receive(EntityPlayer player, String sha1, String name, int total, int offset, byte[] chunk) {
        final UUID id = player.getUniqueID();
        if (total <= 0 || total > SchematicImport.MAX_FILE_BYTES) {
            UPLOADS.remove(id);
            MMUtils.sendErrorToPlayer(player, "mm.info.error.schematic_too_big", name);
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
            MMUtils.sendErrorToPlayer(player, "mm.info.error.schematic_upload_failed", name);
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
            MMUtils.sendErrorToPlayer(player, "mm.info.error.schematic_upload_failed", upload.name);
            return;
        }

        final SchematicData schematic;
        try {
            schematic = SchematicData.parse(upload.data);
        } catch (IOException e) {
            MMUtils.sendErrorToPlayer(player, "mm.info.error.schematic_unreadable", upload.name, e.getMessage());
            return;
        }
        if ((long) schematic.width * schematic.height * schematic.length > SchematicImport.MAX_VOLUME) {
            MMUtils.sendErrorToPlayer(player, "mm.info.error.schematic_too_big", upload.name);
            return;
        }

        try {
            SchematicImport.store(false, upload.sha1, upload.data);
        } catch (IOException e) {
            MMMod.LOG.error("Schematic import: could not store " + upload.name, e);
            MMUtils.sendErrorToPlayer(player, "mm.info.error.schematic_upload_failed", upload.name);
            return;
        }

        final ItemStack held = player.inventory.getCurrentItem();
        if (held == null || !(held.getItem() instanceof ItemMatterManipulator)) {
            MMUtils.sendErrorToPlayer(player, "mm.info.error.schematic_hold_manipulator");
            return;
        }
        final MMState state = ItemMatterManipulator.getState(held);
        if (!state.hasCap(ItemMatterManipulator.ALLOW_COPYING)) {
            MMUtils.sendErrorToPlayer(player, "mm.info.error.schematic_needs_copying");
            return;
        }

        state.config.importedSchematic = new ImportedSchematic(
            upload.name,
            upload.sha1,
            schematic.width,
            schematic.height,
            schematic.length
        );
        state.config.placeMode = PlaceMode.COPYING;
        state.config.action = null;
        ItemMatterManipulator.setState(held, state);

        MMUtils.sendInfoToPlayer(
            player,
            "mm.info.schematic_imported",
            upload.name,
            schematic.width,
            schematic.height,
            schematic.length
        );
        if (!schematic.unknownBlocks.isEmpty()) {
            MMUtils.sendWarningToPlayer(
                player,
                "mm.info.schematic_unknown_blocks",
                schematic.unknownBlocks.values()
                    .stream()
                    .mapToInt(Integer::intValue)
                    .sum(),
                String.join(", ", schematic.unknownBlocks.keySet())
            );
        }
    }
}
