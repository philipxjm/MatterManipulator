package com.recursive_pineapple.matter_manipulator.common.networking;

import java.nio.charset.StandardCharsets;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

import com.recursive_pineapple.matter_manipulator.common.building.schematic.SchematicUploads;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState;

import io.netty.buffer.ByteBuf;

/**
 * Schematic import traffic, on its own channel rather than the manipulator's: a side without this fork does not know
 * the channel and ignores it. So a stock client never receives anything new, and this client's imports simply do
 * nothing on a stock server, while the mod itself stays protocol-compatible with stock Matter Manipulator.
 */
public final class SchematicChannel {

    public static final String NAME = "mm-schematic";
    public static final SimpleNetworkWrapper NET = NetworkRegistry.INSTANCE.newSimpleChannel(NAME);

    private SchematicChannel() {}

    public static void init() {
        NET.registerMessage(ChunkHandler.class, Chunk.class, 0, Side.SERVER);
        NET.registerMessage(ClearHandler.class, Clear.class, 1, Side.SERVER);
    }

    /** Part of a schematic file being imported into the held manipulator (see SchematicUploads). */
    public static class Chunk implements IMessage {

        public String sha1 = "", name = "";
        public int total, offset;
        public byte[] data = new byte[0];

        public Chunk() {}

        public Chunk(String sha1, String name, int total, int offset, byte[] data) {
            this.sha1 = sha1;
            this.name = name;
            this.total = total;
            this.offset = offset;
            this.data = data;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            writeString(buf, sha1);
            writeString(buf, name);
            buf.writeInt(total);
            buf.writeInt(offset);
            buf.writeInt(data.length);
            buf.writeBytes(data);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            sha1 = readString(buf);
            name = readString(buf);
            total = buf.readInt();
            offset = buf.readInt();
            int length = buf.readInt();
            if (length < 0 || length > SchematicUploads.CHUNK_BYTES) throw new IllegalArgumentException("chunk length " + length);
            data = new byte[length];
            buf.readBytes(data);
        }
    }

    public static class ChunkHandler implements IMessageHandler<Chunk, IMessage> {

        @Override
        public IMessage onMessage(Chunk message, MessageContext ctx) {
            SchematicUploads.receive(ctx.getServerHandler().playerEntity, message.sha1, message.name, message.total, message.offset, message.data);
            return null;
        }
    }

    /** Stop copying from an imported schematic. */
    public static class Clear implements IMessage {

        @Override
        public void toBytes(ByteBuf buf) {}

        @Override
        public void fromBytes(ByteBuf buf) {}
    }

    public static class ClearHandler implements IMessageHandler<Clear, IMessage> {

        @Override
        public IMessage onMessage(Clear message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            ItemStack held = player.inventory.getCurrentItem();
            if (held != null && held.getItem() instanceof ItemMatterManipulator) {
                MMState state = ItemMatterManipulator.getState(held);
                state.config.importedSchematic = null;
                ItemMatterManipulator.setState(held, state);
            }
            return null;
        }
    }

    private static void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf) {
        byte[] bytes = new byte[buf.readUnsignedShort()];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
