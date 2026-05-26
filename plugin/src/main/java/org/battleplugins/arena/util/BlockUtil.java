package org.battleplugins.arena.util;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.battleplugins.arena.BattleArena;
import org.battleplugins.arena.competition.map.options.Bounds;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;

public final class BlockUtil {

    public static CompletableFuture<Object> buildClipboard(World world, Bounds bounds) {
        if (Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")) {
            return CompletableFuture.supplyAsync(() -> buildClipboardSync(world, bounds));
        }

        return CompletableFuture.completedFuture(buildClipboardSync(world, bounds));
    }

    private static Object buildClipboardSync(World world, Bounds bounds) {
        CuboidRegion region = new CuboidRegion(
                BlockVector3.at(bounds.getMinX(), bounds.getMinY(), bounds.getMinZ()),
                BlockVector3.at(bounds.getMaxX(), bounds.getMaxY(), bounds.getMaxZ())
        );
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        ForwardExtentCopy copy = new ForwardExtentCopy(BukkitAdapter.adapt(world), region, clipboard, region.getMinimumPoint());

        try {
            Operations.complete(copy);
        } catch (WorldEditException e) {
            BattleArena.getInstance().error("Failed to build clipboard for map in world {}!", world.getName(), e);
            return null;
        }

        return clipboard;
    }

    public static CompletableFuture<Boolean> copyToWorld(Object cachedClipboard, World newWorld, Bounds bounds) {
        if (Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")) {
            return CompletableFuture.supplyAsync(() -> copyToWorldSync(cachedClipboard, newWorld, bounds));
        }

        return CompletableFuture.completedFuture(copyToWorldSync(cachedClipboard, newWorld, bounds));
    }

    private static boolean copyToWorldSync(Object cachedClipboard, World newWorld, Bounds bounds) {
        try (EditSession session = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(newWorld))) {
            Operation operation = new ClipboardHolder((Clipboard) cachedClipboard)
                    .createPaste(session)
                    .ignoreAirBlocks(true) // void world — safe and faster
                    .to(BlockVector3.at(bounds.getMinX(), bounds.getMinY(), bounds.getMinZ()))
                    .build();

            Operations.complete(operation);
        } catch (WorldEditException e) {
            BattleArena.getInstance().error("Failed to paste clipboard when copying region to another world!", e);
            return false;
        }

        return true;
    }
}
