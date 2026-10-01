package com.recursive_pineapple.matter_manipulator.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import com.recursive_pineapple.matter_manipulator.common.building.schematic.SchematicImport;
import com.recursive_pineapple.matter_manipulator.common.building.schematic.SchematicUploads;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState;

/**
 * /mmschematic list | import <file> | clear: imports a schematic from the server's schematics folder into the held
 * manipulator. For players without this fork (stock Matter Manipulator); it needs nothing on the client. Output is
 * plain text, since those clients have no translations for it.
 */
public class SchematicCommand extends CommandBase {

    private static final int PAGE = 15;

    @Override
    public String getCommandName() {
        return "mmschematic";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/mmschematic list [page] | import <file> | clear";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    /** The server's schematics folder (in single player, the game's own .minecraft/schematics). */
    public static File root() {
        return MinecraftServer.getServer()
            .getFile("schematics");
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) throw new WrongUsageException(getCommandUsage(sender));

        switch (args[0]) {
            case "list" -> list(sender, args.length > 1 ? parseIntWithMin(sender, args[1], 1) : 1);
            case "import" -> {
                if (args.length < 2) throw new WrongUsageException("/mmschematic import <file>");
                importFile(player(sender), String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
            }
            case "clear" -> clear(player(sender));
            default -> throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private static EntityPlayerMP player(ICommandSender sender) {
        if (sender instanceof EntityPlayerMP player) return player;
        throw new WrongUsageException("Only players can do that.");
    }

    private void list(ICommandSender sender, int page) {
        final List<String> files = files();
        if (files.isEmpty()) {
            say(sender, EnumChatFormatting.GRAY, "No .schematic files in " + root().getPath());
            return;
        }
        final int pages = (files.size() + PAGE - 1) / PAGE;
        page = Math.min(page, pages);
        say(sender, EnumChatFormatting.GOLD, "Schematics (page " + page + "/" + pages + "), /mmschematic import <file>:");
        for (String file : files.subList((page - 1) * PAGE, Math.min(files.size(), page * PAGE))) {
            say(sender, EnumChatFormatting.GRAY, "  " + file);
        }
    }

    private static void importFile(EntityPlayerMP player, String name) {
        final File root = root();
        File file = new File(root, name);
        if (
            !file.isFile() && !name.toLowerCase()
                .endsWith(".schematic")
        ) {
            file = new File(root, name + ".schematic");
        }

        try {
            // Only files inside the schematics folder.
            final String rootPath = root.getCanonicalPath() + File.separator;
            if (
                !file.getCanonicalPath()
                    .startsWith(rootPath) || !file.isFile()
            ) {
                say(player, EnumChatFormatting.RED, "No schematic " + name + " (see /mmschematic list)");
                return;
            }
            if (file.length() > SchematicImport.MAX_FILE_BYTES) {
                say(player, EnumChatFormatting.RED, name + " is too big to import.");
                return;
            }
            final String relative = root.toURI()
                .relativize(file.toURI())
                .getPath();
            SchematicUploads.apply(player, relative, Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            say(player, EnumChatFormatting.RED, "Could not read " + name + ": " + e.getMessage());
        }
    }

    private static void clear(EntityPlayer player) {
        final ItemStack held = player.inventory.getCurrentItem();
        if (held == null || !(held.getItem() instanceof ItemMatterManipulator)) {
            say(player, EnumChatFormatting.RED, "Hold the Matter Manipulator to clear its imported schematic.");
            return;
        }
        final MMState state = ItemMatterManipulator.getState(held);
        state.config.importedSchematic = null;
        ItemMatterManipulator.setState(held, state);
        say(player, EnumChatFormatting.GRAY, "Cleared the imported schematic; copying from marked regions again.");
    }

    /** .schematic files under the folder, as paths relative to it with '/'. */
    private static List<String> files() {
        final List<String> out = new ArrayList<>();
        final File root = root();
        collect(root, root, out, 0);
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private static void collect(File root, File dir, List<String> out, int depth) {
        final File[] entries = dir.listFiles();
        if (entries == null || depth > 8) return;
        for (File f : entries) {
            if (f.isDirectory()) {
                collect(root, f, out, depth + 1);
            } else if (
                f.getName()
                    .toLowerCase()
                    .endsWith(".schematic")
            ) {
                out.add(
                    root.toURI()
                        .relativize(f.toURI())
                        .getPath()
                );
            }
        }
    }

    private static void say(ICommandSender sender, EnumChatFormatting color, String text) {
        sender.addChatMessage(new ChatComponentText(text).setChatStyle(new ChatStyle().setColor(color)));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "list", "import", "clear");
        if (args.length == 2 && "import".equals(args[0])) { return getListOfStringsFromIterableMatchingLastWord(args, files()); }
        return null;
    }
}
