package org.battleplugins.arena.options;

import org.battleplugins.arena.config.DocumentationSource;
import org.battleplugins.arena.options.types.BooleanArenaOption;
import org.battleplugins.arena.options.types.EnumArenaOption;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Represents an option in an arena.
 *
 * @param <T> the type of option
 */
@DocumentationSource("https://docs.battleplugins.org/books/user-guide/page/option-reference")
public final class ArenaOptionType<T extends ArenaOption> {
    private static final Map<String, ArenaOptionType<?>> OPTION_TYPES = new HashMap<>();

    public static final ArenaOptionType<BooleanArenaOption> BLOCK_BREAK = new ArenaOptionType<>("block-break", BooleanArenaOption::new);
    public static final ArenaOptionType<BooleanArenaOption> BLOCK_PLACE = new ArenaOptionType<>("block-place", BooleanArenaOption::new);
    public static final ArenaOptionType<BooleanArenaOption> BLOCK_DROPS = new ArenaOptionType<>("block-drops", BooleanArenaOption::new);
    public static final ArenaOptionType<BooleanArenaOption> BLOCK_INTERACT = new ArenaOptionType<>("block-interact", BooleanArenaOption::new);
    public static final ArenaOptionType<BooleanArenaOption> ITEM_DROPS = new ArenaOptionType<>("item-drops", BooleanArenaOption::new);
    public static final ArenaOptionType<BooleanArenaOption> KEEP_INVENTORY = new ArenaOptionType<>("keep-inventory", BooleanArenaOption::new);
    public static final ArenaOptionType<BooleanArenaOption> KEEP_EXPERIENCE = new ArenaOptionType<>("keep-experience", BooleanArenaOption::new);
    public static final ArenaOptionType<BooleanArenaOption> HUNGER_DEPLETE = new ArenaOptionType<>("hunger-deplete", BooleanArenaOption::new);
    public static final ArenaOptionType<BooleanArenaOption> TEAM_SELECTION = new ArenaOptionType<>("team-selection", BooleanArenaOption::new);

    public static final ArenaOptionType<EnumArenaOption<DamageOption>> DAMAGE_PLAYERS = new ArenaOptionType<>("damage-players", params -> new EnumArenaOption<>(params, DamageOption.class, "option"));
    public static final ArenaOptionType<EnumArenaOption<DamageOption>> DAMAGE_ENTITIES = new ArenaOptionType<>("damage-entities", params -> new EnumArenaOption<>(params, DamageOption.class, "option"));

    private final String name;
    private final Function<Map<String, String>, T> factory;

    ArenaOptionType(String name, Function<Map<String, String>, T> factory) {
        this.name = name;
        this.factory = factory;

        OPTION_TYPES.put(name, this);
    }

    public String getName() {
        return this.name;
    }

    public T create(Map<String, String> params) {
        return this.factory.apply(params);
    }

    @Nullable
    public static ArenaOptionType<?> get(String name) {
        return OPTION_TYPES.get(name);
    }

    public static <T extends ArenaOption> ArenaOptionType<T> create(String name, Function<Map<String, String>, T> factory) {
        return new ArenaOptionType<>(name, factory);
    }

    public static Set<ArenaOptionType<?>> values() {
        return Set.copyOf(OPTION_TYPES.values());
    }
}
