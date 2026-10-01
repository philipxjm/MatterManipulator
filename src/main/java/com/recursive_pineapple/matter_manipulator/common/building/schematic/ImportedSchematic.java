package com.recursive_pineapple.matter_manipulator.common.building.schematic;

import java.util.Objects;

/**
 * A schematic used as the copy source instead of a marked region (stored in the manipulator's config). The file
 * itself is kept by content hash in {@link SchematicImport}'s store, not in the item.
 */
public class ImportedSchematic {

    /** File name it was imported from, for display. */
    public String name;
    /** SHA-1 of the file, hex: the key it is stored under. */
    public String sha1;
    public int width, height, length;

    public ImportedSchematic() {}

    public ImportedSchematic(String name, String sha1, int width, int height, int length) {
        this.name = name;
        this.sha1 = sha1;
        this.width = width;
        this.height = height;
        this.length = length;
    }

    public ImportedSchematic clone() {
        return new ImportedSchematic(name, sha1, width, height, length);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ImportedSchematic other)) return false;
        return Objects.equals(sha1, other.sha1) && Objects.equals(name, other.name) && width == other.width && height == other.height && length == other.length;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, sha1, width, height, length);
    }

    @Override
    public String toString() {
        return name + " (" + width + "x" + height + "x" + length + ")";
    }
}
