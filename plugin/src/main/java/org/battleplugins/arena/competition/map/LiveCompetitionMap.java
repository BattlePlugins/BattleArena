package org.battleplugins.arena.competition.map;

import net.kyori.adventure.util.TriState;
import org.battleplugins.arena.Arena;
import org.battleplugins.arena.ArenaLike;
import org.battleplugins.arena.competition.LiveCompetition;
import org.battleplugins.arena.competition.map.options.Bounds;
import org.battleplugins.arena.competition.map.options.Spawns;
import org.battleplugins.arena.config.ArenaConfigSerializer;
import org.battleplugins.arena.config.ArenaOption;
import org.battleplugins.arena.config.ParseException;
import org.battleplugins.arena.config.PostProcessable;
import org.battleplugins.arena.util.BlockUtil;
import org.battleplugins.arena.util.Util;
import org.battleplugins.arena.util.VoidChunkGenerator;
import org.battleplugins.arena.BattleArenaConfig;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents a map for a competition which is live on this server.
 */
public class LiveCompetitionMap implements ArenaLike, CompetitionMap, PostProcessable {
    private static final MapFactory FACTORY = MapFactory.create(LiveCompetitionMap.class, LiveCompetitionMap::new);

    @ArenaOption(name = "name", description = "The name of the map.", required = true)
    private String name;

    @ArenaOption(name = "arena", description = "The arena this map is for.", required = true)
    private Arena arena;

    @ArenaOption(name = "type", description = "The type of map.", required = true)
    private MapType type;

    @ArenaOption(name = "world", description = "The world the map is located in.", required = true)
    private String world;

    @ArenaOption(name = "bounds", description = "The bounds of the map.")
    private Bounds bounds;

    @ArenaOption(name = "spawns", description = "The spawn locations.")
    private Spawns spawns;

    private World mapWorld;
    private World parentWorld;

    public LiveCompetitionMap() {
    }

    public LiveCompetitionMap(String name, Arena arena, MapType type, String world, @Nullable Bounds bounds, @Nullable Spawns spawns) {
        this.name = name;
        this.arena = arena;
        this.type = type;
        this.world = world;
        this.bounds = bounds;
        this.spawns = spawns;
    }

    @Override
    public void postProcess() {
        if (this.mapWorld != null) {
            return; // Map was already set in createDynamicCompetition
        }

        this.mapWorld = Bukkit.getWorld(this.world);
        if (this.mapWorld == null) {
            throw new IllegalStateException("World " + this.world + " for map " + this.name + " in arena " + this.arena.getName() + " does not exist!");
        }
    }

    /**
     * Creates a new competition for this map.
     *
     * @param arena the arena to create the competition for
     * @return the created competition
     */
    public LiveCompetition<?> createCompetition(Arena arena) {
        return new LiveCompetition<>(arena, arena.getType(), this);
    }

    public void save() throws ParseException, IOException {
        Path mapsPath = this.arena.getMapPath();
        if (Files.notExists(mapsPath)) {
            Files.createDirectories(mapsPath);
        }

        Path mapPath = mapsPath.resolve(this.getName().toLowerCase(Locale.ROOT) + ".yml");
        if (Files.notExists(mapPath)) {
            Files.createFile(mapPath);
        }

        FileConfiguration configuration = YamlConfiguration.loadConfiguration(Files.newBufferedReader(mapPath));
        ArenaConfigSerializer.serialize(this, configuration);

        configuration.save(mapPath.toFile());
    }

    @Override
    public final String getName() {
        return this.name;
    }

    /**
     * Sets the name of the map.
     *
     * @param name the name of the map
     */
    public final void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the {@link Arena} this map belongs to.
     *
     * @return the arena this map belongs to
     */
    @Override
    public final Arena getArena() {
        return this.arena;
    }

    /**
     * Gets the {@link MapType} of this map.
     *
     * @return the map type of this map
     */
    @Override
    public final MapType getType() {
        return this.type;
    }

    /**
     * Sets the type of the map.
     *
     * @param type the type of the map
     */
    public final void setType(MapType type) {
        this.type = type;
    }

    /**
     * Gets the {@link World} this map is located in.
     *
     * @return the world this map is located in
     */
    public final World getWorld() {
        return this.mapWorld;
    }

    /**
     * Gets the parent world of the map.
     * <p>
     * This is the world that the map is located in, or the parent world
     * if the map is a dynamic map.
     *
     * @return the parent world of the map
     */
    public final World getParentWorld() {
        return this.parentWorld == null ? this.mapWorld : this.parentWorld;
    }

    /**
     * Gets the {@link Bounds} of the map.
     *
     * @return the bounds of the map
     */
    public final Optional<Bounds> bounds() {
        return Optional.ofNullable(this.bounds);
    }

    /**
     * Gets the bounds of the map.
     *
     * @return the bounds of the map, or null if there are no bounds
     */
    @Nullable
    public final Bounds getBounds() {
        return this.bounds;
    }

    /**
     * Sets the bounds of the map.
     *
     * @param bounds the bounds of the map
     */
    public final void setBounds(Bounds bounds) {
        this.bounds = bounds;
    }

    /**
     * Gets the {@link Spawns} of the map.
     *
     * @return the spawn locations of the map
     */
    public final Optional<Spawns> spawns() {
        return Optional.ofNullable(this.spawns);
    }

    /**
     * Gets the spawn locations of the map.
     *
     * @return the spawn locations of the map, or null if there are no spawn locations
     */
    @Nullable
    public final Spawns getSpawns() {
        return this.spawns;
    }

    /**
     * Sets the spawn locations of the map.
     *
     * @param spawns the spawn locations of the map
     */
    public final void setSpawns(Spawns spawns) {
        this.spawns = spawns;
    }

    /**
     * Creates a new dynamic competition for this map.
     * <p>
     * This is only supported for maps with a {@link MapType}
     * of type {@link MapType#DYNAMIC}.
     *
     * @param arena the arena to create the competition for
     * @return the created dynamic competition
     */
    @Nullable
    public final LiveCompetition<?> createDynamicCompetition(Arena arena) {
        if (this.type != MapType.DYNAMIC) {
            throw new IllegalStateException("Cannot create dynamic competition for non-dynamic map!");
        }

        String worldName = "ba-dynamic-" + UUID.randomUUID();
        World world = Bukkit.createWorld(WorldCreator.name(worldName)
                .generator(VoidChunkGenerator.INSTANCE)
                .environment(World.Environment.NORMAL)
                .generateStructures(false)
                .keepSpawnLoaded(TriState.FALSE)
                .type(WorldType.NORMAL)
        );

        if (world == null) {
            return null;
        }
        world.setGameRule(GameRule.DISABLE_RAIDS, true);

        BattleArenaConfig config = this.getArena().getPlugin().getMainConfig();

        // If schematic usage is disabled in the config OR schematic pasting fails,
        // then attempt to fall back to copying the map directly from the map world.
        // If that also fails, return null to indicate map setup failure.
        if ((!config.isSchematicUsage() || !BlockUtil.pasteSchematic(this))
            && !BlockUtil.copyToWorld(this.mapWorld, world, this.bounds, config.centerDynamicArena())) {
            return null;
        }

        LiveCompetitionMap copy = arena.getMapFactory().create(this.name, arena, this.type, worldName, this.bounds, this.spawns);
        // Copy additional fields for custom maps
        if (copy.getClass() != LiveCompetitionMap.class) {
            Util.copyFields(this, copy);
        }

        copy.mapWorld = world;
        copy.parentWorld = this.mapWorld;
        copy.postProcess();

        return copy.createCompetition(arena);
    }

    /**
     * Gets the default factory for creating {@link LiveCompetitionMap live maps}.
     *
     * @return the factory for creating maps
     */
    public static MapFactory getFactory() {
        return FACTORY;
    }
}
