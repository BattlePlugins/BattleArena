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
import org.battleplugins.arena.competition.map.LiveCompetitionMap;
import org.bukkit.Bukkit;
import org.bukkit.World;

public final class BlockUtil {

    public static boolean copyToWorld(World oldWorld, World newWorld, Bounds bounds, boolean center) {
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

        BlockVector3 pasteLocation;

        if (center) {
            int widthX = bounds.getMaxX() - bounds.getMinX();
            int Y = bounds.getMinY();
            int widthZ = bounds.getMaxZ() - bounds.getMinZ();

            pasteLocation = BlockVector3.at(
                -widthX / 2,
                Y,
                -widthZ / 2
            );
        } else {
            pasteLocation = BlockVector3.at(bounds.getMinX(), bounds.getMinY(), bounds.getMinZ());
        }

        try (EditSession session = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(newWorld))) {
            Operation operation = new ClipboardHolder(clipboard).createPaste(session)
                    .to(pasteLocation)
                    .build();

            Operations.complete(operation);
        } catch (WorldEditException e) {
            // Error pasting schematic
            BattleArena.getInstance().error("Failed to paste copy when copying region to another world!", e);
            return false;
        }

        return true;
    }

    public static boolean pasteSchematic(LiveCompetitionMap map, World World) {
        Path path = map.getArena().getPlugin().getDataFolder().toPath()
            .resolve("schematics")
            .resolve(map.getArena().getName().toLowerCase(Locale.ROOT))
            .resolve(map.getName().toLowerCase(Locale.ROOT) + "." +
                BuiltInClipboardFormat.SPONGE_SCHEMATIC.getPrimaryFileExtension()
            );
        if (Files.notExists(path)) {
            Bukkit.getLogger().warning("Schematic not found: " + path.toString());
            return false;
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
    
        BlockVector3 pasteLocation;
        Bounds bounds = map.getBounds();

        if (map.getArena().getPlugin().getMainConfig().centerDynamicArena()) {
            int widthX = bounds.getMaxX() - bounds.getMinX();
            int Y = bounds.getMinY();
            int widthZ = bounds.getMaxZ() - bounds.getMinZ();

            pasteLocation = BlockVector3.at(
                -widthX / 2,
                Y,
                -widthZ / 2
            );
        } else {
            pasteLocation = BlockVector3.at(bounds.getMinX(), bounds.getMinY(), bounds.getMinZ());
        }

        try (EditSession session = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(World))) {
            Operation operation = new ClipboardHolder(clipboard).createPaste(session)
                .to(pasteLocation)
                .build();

            Operations.complete(operation);
            return true;
        } catch (WorldEditException e) {
            Bukkit.getLogger().severe("Failed to paste schematic: " + e.getMessage());
            return false;
        }
    }
}
