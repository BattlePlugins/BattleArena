package org.battleplugins.arena.util;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.battleplugins.arena.BattleArena;
import org.battleplugins.arena.competition.map.options.Bounds;
import org.bukkit.Bukkit;
import org.bukkit.World;

public final class BlockUtil {

    public static boolean copyToWorld(World oldWorld, World newWorld, Bounds bounds) {
        CuboidRegion region = new CuboidRegion(BlockVector3.at(bounds.getMinX(), bounds.getMinY(), bounds.getMinZ()), BlockVector3.at(bounds.getMaxX(), bounds.getMaxY(), bounds.getMaxZ()));
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        ForwardExtentCopy copy = new ForwardExtentCopy(BukkitAdapter.adapt(oldWorld), region, clipboard, region.getMinimumPoint());

        try {
            Operations.complete(copy);
        } catch (WorldEditException e) {
            // Error creating schematic
            BattleArena.getInstance().error("Failed to create copy when copying region to another world!",  e);
            return false;
        }

        try (EditSession session = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(newWorld))) {
            Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(session)
                    .ignoreAirBlocks(true) // its a void world so no issue. its a heavy optimization.
                    .to(BlockVector3.at(bounds.getMinX(), bounds.getMinY(), bounds.getMinZ()))
                    .build();

            Operations.complete(operation);
        } catch (WorldEditException e) {
            // Error pasting schematic
            BattleArena.getInstance().error("Failed to paste copy when copying region to another world!", e);
            return false;
        }

        return true;
    }

    public static boolean pasteSchematic(String map, String arena, World world, Bounds bounds) {
        Path path = BattleArena.getInstance().getDataFolder().toPath()
                .resolve("schematics")
                .resolve(arena.toLowerCase(Locale.ROOT))
                .resolve(map.toLowerCase(Locale.ROOT) + "." +
                        BuiltInClipboardFormat.SPONGE_SCHEMATIC.getPrimaryFileExtension()
                );

        if (Files.notExists(path)) {
            Bukkit.getLogger().warning("Schematic not found: " + path);
            path = BattleArena.getInstance().getDataFolder().toPath()
                .resolve("schematics")
                .resolve(arena.toLowerCase(Locale.ROOT))
                .resolve(map.toLowerCase(Locale.ROOT) + "." +
                        BuiltInClipboardFormat.MCEDIT_SCHEMATIC.getPrimaryFileExtension()
                );
            if (Files.notExists(path)) {
                Bukkit.getLogger().warning("Schematic not found: " + path);
                return false;
            }
        }

        ClipboardFormat format = ClipboardFormats.findByFile(path.toFile());
        if (format == null) {
            Bukkit.getLogger().warning("Unknown schematic format: " + path.getFileName());
            return false;
        }

        Clipboard clipboard;
        try (ClipboardReader reader = format.getReader(Files.newInputStream(path))) {
            clipboard = reader.read();
        } catch (IOException e) {
            Bukkit.getLogger().severe("Failed to read schematic: " + e.getMessage());
            return false;
        }

        try (EditSession session = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(session)
                    .to(BlockVector3.at(bounds.getMinX(), bounds.getMinY(), bounds.getMinZ()))
                    .ignoreAirBlocks(true)
                    .build();

            Operations.complete(operation);
            return true;
        } catch (WorldEditException e) {
            Bukkit.getLogger().severe("Failed to paste schematic: " + e.getMessage());
            return false;
        }
    }
}
