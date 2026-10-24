package com.recursive_pineapple.matter_manipulator.common.building.schematic;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import net.minecraftforge.common.util.Constants;

import cpw.mods.fml.common.registry.GameData;

/**
 * A parsed .schematic file: Schematica's "Alpha" format (Blocks, Data, AddBlocks, SchematicaMapping, TileEntities),
 * which is what Schematica and gtnh-blueprint write, plus plain MCEdit files without a name mapping (numeric ids,
 * taken as this game's ids). Index of (x, y, z) is (y * length + z) * width + x.
 */
public class SchematicData {

    public final int width, height, length;
    public final Block[] blocks;
    /** Metadata: GTNH's goes past 15 (a GT frame box keeps its material in it). */
    public final int[] meta;
    /** Tile entity NBT by index, with x/y/z relative to the schematic. */
    public final Map<Integer, NBTTagCompound> tiles = new HashMap<>();
    /** Registry names in the file that this game does not have; those blocks read as air. */
    public final Map<String, Integer> unknownBlocks = new HashMap<>();

    private SchematicData(int width, int height, int length) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.blocks = new Block[width * height * length];
        this.meta = new int[width * height * length];
    }

    public int index(int x, int y, int z) {
        return (y * this.length + z) * this.width + x;
    }

    public boolean contains(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < this.width && y < this.height && z < this.length;
    }

    public static SchematicData parse(byte[] bytes) throws IOException {
        NBTTagCompound root;
        try {
            root = CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            // Schematica also reads uncompressed files.
            root = CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(bytes)));
        }
        return parse(root);
    }

    public static SchematicData parse(NBTTagCompound tag) throws IOException {
        final int width = tag.getShort("Width"), height = tag.getShort("Height"), length = tag.getShort("Length");
        if (width <= 0 || height <= 0 || length <= 0) { throw new IOException("not a schematic (size " + width + "x" + height + "x" + length + ")"); }
        final int n = width * height * length;
        final byte[] ids = tag.getByteArray("Blocks");
        final byte[] data = tag.getByteArray("Data");
        // The high byte of metadata past 255 (the Schematica fork writes it).
        final byte[] dataHigh = tag.getByteArray("AddData");
        if (ids.length != n || data.length != n) { throw new IOException("Blocks/Data length does not match the size"); }
        // High bits of block ids: one byte per block ("Add", which the Schematica fork writes for ids past 4095 next
        // to the nibbles, or an AddBlocks that is one byte per block from the schematicplus format), else a nibble
        // array ("AddBlocks", MCEdit/Schematica), which only reaches id 4095.
        byte[] add = null;
        boolean addPerBlock = false;
        if (tag.hasKey("Add") && tag.getByteArray("Add").length == n) {
            add = tag.getByteArray("Add");
            addPerBlock = true;
        } else if (tag.hasKey("AddBlocks")) {
            add = tag.getByteArray("AddBlocks");
            addPerBlock = add.length == n;
        }

        final Map<Integer, Block> byFileId = new HashMap<>();
        final Map<Integer, String> nameByFileId = new HashMap<>();
        final boolean mapped = tag.hasKey("SchematicaMapping");
        if (mapped) {
            final NBTTagCompound mapping = tag.getCompoundTag("SchematicaMapping");
            for (Object key : mapping.func_150296_c()) {
                final String name = (String) key;
                final int id = mapping.getShort(name);
                nameByFileId.put(id, name);
                final Block block = GameData.getBlockRegistry()
                    .getObject(name);
                // The registry returns air for unknown names; only trust it for air itself.
                if (block != null && (block != Blocks.air || "minecraft:air".equals(name))) {
                    byFileId.put(id, block);
                }
            }
        }

        final SchematicData schematic = new SchematicData(width, height, length);
        for (int i = 0; i < n; i++) {
            int id = ids[i] & 0xff;
            if (add != null) {
                if (addPerBlock) {
                    id |= (add[i] & 0xff) << 8;
                } else if ((i >> 1) < add.length) {
                    id |= ((add[i >> 1] >> ((i & 1) != 0 ? 0 : 4)) & 0xf) << 8;
                }
            }
            if (id == 0) {
                schematic.blocks[i] = Blocks.air;
                continue;
            }
            Block block = mapped ? byFileId.get(id) : Block.getBlockById(id);
            if (block == null || (block == Blocks.air && !mapped)) {
                schematic.unknownBlocks.merge(nameByFileId.getOrDefault(id, "#" + id), 1, Integer::sum);
                block = Blocks.air;
            }
            schematic.blocks[i] = block;
            schematic.meta[i] = (data[i] & 0xff) | (i < dataHigh.length ? (dataHigh[i] & 0xff) << 8 : 0);
        }

        final NBTTagList tiles = tag.getTagList("TileEntities", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tiles.tagCount(); i++) {
            final NBTTagCompound tile = tiles.getCompoundTagAt(i);
            final int x = tile.getInteger("x"), y = tile.getInteger("y"), z = tile.getInteger("z");
            if (schematic.contains(x, y, z)) {
                schematic.tiles.put(schematic.index(x, y, z), tile);
            }
        }
        return schematic;
    }
}
