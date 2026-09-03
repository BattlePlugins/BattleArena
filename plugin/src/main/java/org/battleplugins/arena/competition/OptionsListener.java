package org.battleplugins.arena.competition;

import org.battleplugins.arena.ArenaPlayer;
import org.battleplugins.arena.BattleArena;
import org.battleplugins.arena.event.ArenaEventHandler;
import org.battleplugins.arena.event.ArenaListener;
import org.battleplugins.arena.messages.Messages;
import org.battleplugins.arena.options.ArenaOptionType;
import org.battleplugins.arena.options.DamageOption;
import org.battleplugins.arena.options.types.BooleanArenaOption;
import org.battleplugins.arena.options.types.CommandBlockArenaOption;
import org.battleplugins.arena.options.types.EnumArenaOption;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Locale;

class OptionsListener<T extends Competition<T>> implements ArenaListener, CompetitionLike<T> {
    private final LiveCompetition<T> competition;

    public OptionsListener(LiveCompetition<T> competition) {
        this.competition = competition;
    }

    @ArenaEventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!this.competition.option(ArenaOptionType.BLOCK_BREAK).map(BooleanArenaOption::isEnabled).orElse(true)) {
            event.setCancelled(true);
        }

        if (!this.competition.option(ArenaOptionType.BLOCK_DROPS).map(BooleanArenaOption::isEnabled).orElse(true)) {
            event.setDropItems(false);
        }
    }

    @ArenaEventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!this.competition.option(ArenaOptionType.BLOCK_PLACE).map(BooleanArenaOption::isEnabled).orElse(true)) {
            event.setCancelled(true);
        }
    }

    @ArenaEventHandler(priority = EventPriority.LOWEST)
    public void onDropItem(PlayerDropItemEvent event) {
        if (!this.competition.option(ArenaOptionType.ITEM_DROPS).map(BooleanArenaOption::isEnabled).orElse(true)) {
            event.setCancelled(true);
        }
    }

    @ArenaEventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (!this.competition.option(ArenaOptionType.BLOCK_INTERACT).map(BooleanArenaOption::isEnabled).orElse(true)) {
                event.setCancelled(true);
            }
        }
    }

    @ArenaEventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        this.competition.option(ArenaOptionType.KEEP_INVENTORY)
                .map(BooleanArenaOption::isEnabled)
                .ifPresent(keepInventory -> {
                    event.setKeepInventory(keepInventory);
                    if (keepInventory) {
                        event.getDrops().clear();
                    }
                });

        this.competition.option(ArenaOptionType.KEEP_EXPERIENCE)
                .map(BooleanArenaOption::isEnabled)
                .ifPresent(keepLevels -> {
                    event.setKeepLevel(keepLevels);
                    if (keepLevels) {
                        event.setDroppedExp(0);
                    }
                });
    }

    @ArenaEventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Player damager;
        if (event.getDamager() instanceof Player eventDamager) {
            damager = eventDamager;
        } else if (event.getDamager() instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter) {
                damager = shooter;
            } else {
                return;
            }
        } else {
            return;
        }

        if (!(event.getEntity() instanceof Player damaged)) {
            DamageOption damageOption = this.competition.option(ArenaOptionType.DAMAGE_ENTITIES)
                    .map(EnumArenaOption::getOption)
                    .orElse(DamageOption.ALWAYS);

            if (damageOption == DamageOption.NEVER) {
                event.setCancelled(true);
            }
        } else {
            // Player damage checking is slightly more complicated
            DamageOption damageOption = this.competition.option(ArenaOptionType.DAMAGE_PLAYERS)
                    .map(EnumArenaOption::getOption)
                    .orElse(DamageOption.ALWAYS);

            // If the damage option is always, then assume damage is enabled in
            // any case and just return here
            if (damageOption == DamageOption.ALWAYS) {
                return;
            }

            ArenaPlayer damagerPlayer = ArenaPlayer.getArenaPlayer(damager);
            ArenaPlayer damagedPlayer = ArenaPlayer.getArenaPlayer(damaged);

            // Ensure that both players exist and are in an arena
            if ((damagerPlayer == null && damagedPlayer != null) || (damagerPlayer != null && damagedPlayer == null)) {
                return;
            }

            // Check to see if players are in the same arena
            if (damagerPlayer != null && !damagerPlayer.getCompetition().equals(damagedPlayer.getCompetition())) {
                return;
            }

            // If the damage option is never, then cancel the event
            if (damageOption == DamageOption.NEVER) {
                event.setCancelled(true);
                return;
            }

            // Cancel the event if the damage option is other team and the
            // players are on the same team
            if (damageOption == DamageOption.OTHER_TEAM && damagerPlayer != null) {
                if (damagerPlayer.getTeam() != null && damagedPlayer.getTeam() != null) {
                    if (!damagerPlayer.getTeam().isHostileTo(damagedPlayer.getTeam())) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @ArenaEventHandler(priority = EventPriority.LOWEST)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!this.competition.option(ArenaOptionType.HUNGER_DEPLETE).map(BooleanArenaOption::isEnabled).orElse(true)) {
            event.setCancelled(true);
        }
    }

    @ArenaEventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        CommandBlockArenaOption option = this.competition.option(ArenaOptionType.BLOCK_COMMANDS).orElse(null);
        if (option == null || !option.isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission("battlearena.command.bypass")) {
            return;
        }

        String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (this.isBattleArenaCommand(label) || option.isWhitelisted(label)) {
            return;
        }

        event.setCancelled(true);
        Messages.COMMAND_BLOCKED.send(player);
    }

    private boolean isBattleArenaCommand(String label) {
        Command command = Bukkit.getCommandMap().getCommand(label);
        return command instanceof PluginCommand pluginCommand && pluginCommand.getPlugin().equals(BattleArena.getInstance());
    }

    @SuppressWarnings("unchecked")
    @Override
    public T getCompetition() {
        return (T) this.competition;
    }
}
