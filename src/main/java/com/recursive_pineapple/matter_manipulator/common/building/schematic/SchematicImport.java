package com.recursive_pineapple.matter_manipulator.common.building.schematic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.World;

import net.minecraftforge.common.DimensionManager;

import cpw.mods.fml.common.Loader;

import com.recursive_pineapple.matter_manipulator.MMMod;
import com.recursive_pineapple.matter_manipulator.common.building.BlockAnalyzer.RegionAnalysis;
import com.recursive_pineapple.matter_manipulator.common.building.BlockSpec;
import com.recursive_pineapple.matter_manipulator.common.building.PendingBlock;

import org.joml.Vector3i;

/**
 * Schematic import for copy mode: the store (by SHA-1, client copy under .minecraft/matter-manipulator/schematics,
 * server
 * copy in the world save), and the analysis that turns a schematic into the same PendingBlocks a copied region gives
 * (relative to zero; MMState then rotates, offsets to coord C and arrays them as usual).
 */
public final class SchematicImport {

    /** Largest schematic accepted, in blocks (about a 160-block cube). */
    public static final int MAX_VOLUME = 4_096_000;
    /** Largest schematic file accepted, in bytes. */
    public static final int MAX_FILE_BYTES = 16 * 1024 * 1024;

    private SchematicImport() {}

    // #region Store

    /** The client's copies: .minecraft/matter-manipulator/schematics (config dir's parent is the game dir). */
    public static File clientDir() {
        return new File(
            Loader.instance()
                .getConfigDir()
                .getParentFile(),
            "matter-manipulator/schematics"
        );
    }

    /** The server's copies, inside the world save so they travel with the world. */
    public static File serverDir() {
        final File root = DimensionManager.getCurrentSaveRootDirectory();
        return new File(root != null ? root : new File("."), "matter-manipulator/schematics");
    }

    private static File file(boolean client, String sha1) {
        return new File(client ? clientDir() : serverDir(), sha1 + ".schematic");
    }

    public static void store(boolean client, String sha1, byte[] bytes) throws IOException {
        final File file = file(client, sha1);
        if (file.isFile() && file.length() == bytes.length) return;
        file.getParentFile()
            .mkdirs();
        final File tmp = new File(file.getParentFile(), sha1 + ".tmp");
        Files.write(tmp.toPath(), bytes);
        if (!tmp.renameTo(file)) {
            Files.write(file.toPath(), bytes);
            tmp.delete();
        }
    }

    public static byte[] read(boolean client, String sha1) throws IOException {
        final File file = file(client, sha1);
        return file.isFile() ? Files.readAllBytes(file.toPath()) : null;
    }

    public static String sha1(byte[] bytes) {
        try {
            final StringBuilder hex = new StringBuilder();
            for (
                byte b : MessageDigest.getInstance("SHA-1")
                    .digest(bytes)
            ) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // #endregion

    // #region Analysis

    private static final class Analysis {

        final Vector3i deltas;
        final List<PendingBlock> blocks;

        Analysis(Vector3i deltas, List<PendingBlock> blocks) {
            this.deltas = deltas;
            this.blocks = blocks;
        }
    }

    /** Analyses by "client|server sha1", most recent last. Parsing a schematic is far slower than cloning. */
    private static final Map<String, Analysis> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(8, 0.75f, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Analysis> eldest) {
            return size() > 4;
        }
    });
    private static final Set<String> REPORTED = Collections.synchronizedSet(new HashSet<>());

    /**
     * The blocks of an imported schematic as a copy analysis (positions relative to zero, in the real world's
     * dimension), or null if this side does not have the file.
     */
    public static RegionAnalysis analyze(World world, ImportedSchematic ref) {
        if (ref == null || ref.sha1 == null) return null;
        final boolean client = world.isRemote;
        final String key = (client ? "client " : "server ") + ref.sha1;

        Analysis base = CACHE.get(key);
        if (base == null) {
            final byte[] bytes;
            final SchematicData schematic;
            try {
                bytes = read(client, ref.sha1);
                if (bytes == null) {
                    if (REPORTED.add(key)) {
                        MMMod.LOG.warn("Schematic import: {} has no copy of {} ({})", client ? "client" : "server", ref.name, ref.sha1);
                    }
                    return null;
                }
                schematic = SchematicData.parse(bytes);
            } catch (IOException e) {
                if (REPORTED.add(key)) MMMod.LOG.error("Schematic import: could not read " + ref.name, e);
                return null;
            }
            base = analyze(new SchematicWorld(world, schematic));
            CACHE.put(key, base);
        }

        final RegionAnalysis analysis = new RegionAnalysis();
        analysis.deltas = new Vector3i(base.deltas);
        analysis.blocks = new ArrayList<>(base.blocks.size());
        final int dim = world.provider.dimensionId;
        for (PendingBlock block : base.blocks) {
            final PendingBlock dup = block.clone();
            dup.worldId = dim;
            analysis.blocks.add(dup);
        }
        return analysis;
    }

    /** Same per-block steps as BlockAnalyzer.analyzeRegion, over the schematic. */
    private static Analysis analyze(SchematicWorld world) {
        final SchematicData schematic = world.getSchematic();
        final long start = System.nanoTime();
        final List<PendingBlock> blocks = new ArrayList<>();
        int failed = 0;

        for (int y = 0; y < schematic.height; y++) {
            for (int z = 0; z < schematic.length; z++) {
                for (int x = 0; x < schematic.width; x++) {
                    try {
                        final BlockSpec spec = BlockSpec.fromBlock(null, world, x, y, z);
                        if (spec.skipWhenCopying()) continue;

                        final PendingBlock pending = spec.instantiate(world, x, y, z);
                        pending.analyze(world.getTileEntity(x, y, z), PendingBlock.ANALYZE_ALL & ~PendingBlock.ANALYZE_ARCH);

                        // Multiparts need their neighbours placed first (as in BlockAnalyzer).
                        if (pending.mp != null) {
                            pending.buildOrder = Math.max(pending.buildOrder, 100);
                        }
                        blocks.add(pending);
                    } catch (Throwable t) {
                        if (failed++ == 0) {
                            MMMod.LOG.warn("Schematic import: could not analyse the block at " + x + " " + y + " " + z, t);
                        }
                    }
                }
            }
        }

        if (!schematic.unknownBlocks.isEmpty()) {
            MMMod.LOG.warn("Schematic import: blocks this game does not have (left out): {}", schematic.unknownBlocks);
        }
        MMMod.LOG.debug(
            "Schematic import: analysed {}x{}x{} into {} blocks in {} ms{}",
            schematic.width,
            schematic.height,
            schematic.length,
            blocks.size(),
            (System.nanoTime() - start) / 1_000_000,
            failed > 0 ? " (" + failed + " could not be analysed)" : ""
        );

        return new Analysis(new Vector3i(schematic.width - 1, schematic.height - 1, schematic.length - 1), blocks);
    }

    // #endregion
}
