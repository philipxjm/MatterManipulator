package com.recursive_pineapple.matter_manipulator.common.building.schematic;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import codechicken.multipart.TileMultipart;

/**
 * Forge Multipart side of schematic loading; only touched when Forge Multipart is installed.
 */
final class SchematicMultipart {

    private SchematicMultipart() {}

    /** A schematic's "savedMultipart" tile (strips, covers...) as the TileMultipart the copy analysis reads. */
    static TileEntity load(NBTTagCompound tag) {
        return TileMultipart.createFromNBT(tag);
    }
}
