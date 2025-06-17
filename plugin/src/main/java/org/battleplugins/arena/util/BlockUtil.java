package org.battleplugins.arena.util;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.battleplugins.arena.BattleArena;
import org.battleplugins.arena.competition.map.options.Bounds;
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
}
