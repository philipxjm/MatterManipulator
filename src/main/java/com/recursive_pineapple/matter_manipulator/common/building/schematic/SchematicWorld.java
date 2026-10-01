package com.recursive_pineapple.matter_manipulator.common.building.schematic;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.storage.IPlayerFileData;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

import com.gtnewhorizon.structurelib.util.XSTR;
import com.recursive_pineapple.matter_manipulator.MMMod;
import com.recursive_pineapple.matter_manipulator.common.utils.Mods;

/**
 * A read-only world holding one schematic at (0, 0, 0)..(width-1, height-1, length-1), so the copy analysis
 * (BlockSpec.fromBlock, PendingBlock.analyze) can read it exactly like a copied region of a real world. Anything
 * outside the schematic is air; lighting and biomes come from the real world, as in ProxiedWorld.
 */
public class SchematicWorld extends World {

    private final World world;
    private final SchematicData schematic;
    private final Map<Integer, TileEntity> tiles = new HashMap<>();

    public SchematicWorld(World world, SchematicData schematic) {
        super(new ISaveHandler() {

            @Override
            public void saveWorldInfoWithPlayer(WorldInfo worldInfo, NBTTagCompound nbtTagCompound) {}

            @Override
            public void saveWorldInfo(WorldInfo worldInfo) {}

            @Override
            public WorldInfo loadWorldInfo() {
                return null;
            }

            @Override
            public IPlayerFileData getSaveHandler() {
                return null;
            }

            @Override
            public File getMapFileFromName(String mapName) {
                return null;
            }

            @Override
            public IChunkLoader getChunkLoader(WorldProvider worldProvider) {
                return null;
            }

            @Override
            public void flush() {}

            @Override
            public void checkSessionLock() {}

            @Override
            public String getWorldDirectoryName() {
                return null;
            }

            @Override
            public File getWorldDirectory() {
                return null;
            }
        }, "MM_SCHEMATIC", null, new WorldSettings(new WorldInfo(new NBTTagCompound())), new Profiler());

        this.rand = new XSTR();
        // This World constructor stores the provider as given (null); the analysis reads provider.dimensionId for
        // every block (ImmutableBlockSpec.instantiate), so borrow the real world's.
        this.provider = world.provider;
        this.world = world;
        this.schematic = schematic;
        this.chunkProvider = world.getChunkProvider();
        this.isRemote = world.isRemote;

        for (Map.Entry<Integer, NBTTagCompound> e : schematic.tiles.entrySet()) {
            final NBTTagCompound tag = (NBTTagCompound) e.getValue()
                .copy();
            try {
                TileEntity tile = null;
                if ("savedMultipart".equals(tag.getString("id")) && Mods.ForgeMultipart.isModLoaded()) {
                    // Forge Multipart saves parts under this id and only converts them in a ticking world.
                    tile = SchematicMultipart.load(tag);
                }
                if (tile == null) {
                    tile = TileEntity.createAndLoadEntity(tag);
                }
                if (tile != null) {
                    tile.setWorldObj(this);
                    this.tiles.put(e.getKey(), tile);
                }
            } catch (Throwable t) {
                MMMod.LOG.warn("Schematic import: could not load tile entity " + tag.getString("id"), t);
            }
        }
    }

    public SchematicData getSchematic() {
        return this.schematic;
    }

    @Override
    protected IChunkProvider createChunkProvider() {
        return null;
    }

    @Override
    public Entity getEntityByID(int id) {
        return null;
    }

    @Override
    public boolean setBlock(int x, int y, int z, Block block, int meta, int flags) {
        return true;
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        return this.schematic.contains(x, y, z) ? this.schematic.blocks[this.schematic.index(x, y, z)] : Blocks.air;
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        return this.schematic.contains(x, y, z) ? this.schematic.meta[this.schematic.index(x, y, z)] : 0;
    }

    @Override
    public TileEntity getTileEntity(int x, int y, int z) {
        return this.schematic.contains(x, y, z) ? this.tiles.get(this.schematic.index(x, y, z)) : null;
    }

    @Override
    public boolean isAirBlock(int x, int y, int z) {
        return getBlock(x, y, z).isAir(this, x, y, z);
    }

    @Override
    public boolean blockExists(int x, int y, int z) {
        return this.schematic.contains(x, y, z);
    }

    @Override
    public float getSunBrightnessFactor(float partialTicks) {
        return this.world.getSunBrightnessFactor(partialTicks);
    }

    @Override
    public BiomeGenBase getBiomeGenForCoords(int x, int z) {
        return this.world.getBiomeGenForCoords(x, z);
    }

    @Override
    public int getFullBlockLightValue(int x, int y, int z) {
        return 15;
    }

    @Override
    public boolean canBlockSeeTheSky(int x, int y, int z) {
        return true;
    }

    @Override
    protected int func_152379_p() {
        return 0;
    }
}
