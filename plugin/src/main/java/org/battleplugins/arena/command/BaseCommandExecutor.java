package org.battleplugins.arena.command;

import io.papermc.paper.math.Position;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.battleplugins.arena.Arena;
import org.battleplugins.arena.BattleArena;
import org.battleplugins.arena.config.DurationParser;
import org.battleplugins.arena.config.ItemStackParser;
import org.battleplugins.arena.config.ParseException;
import org.battleplugins.arena.messages.Messages;
import org.battleplugins.arena.util.PaginationCalculator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class BaseCommandExecutor implements TabExecutor {
    private static final int COMMANDS_PER_PAGE = 10;

    protected final String parentCommand;
    protected final String permissionSubNode;

    private final Map<String, Set<CommandWrapper>> commandWrappers = new HashMap<>();

    private final List<SubCommandExecutor> subCommandExecutors = new ArrayList<>();

    public BaseCommandExecutor(String parentCommand) {
        this(parentCommand, null);
    }

    public BaseCommandExecutor(String parentCommand, @Nullable String permissionSubNode) {
        this.parentCommand = parentCommand;
        this.permissionSubNode = permissionSubNode;

        this.registerCommands();
    }

    @Override
    public final boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            this.sendNoArgumentMessage(sender);
            return true;
        }

        if ("help".equals(args[0])) {
            try {
                int page = args.length > 1 ? Integer.parseInt(args[1]) : 1;
                this.sendHelpMessage(sender, page);
            } catch (NumberFormatException e) {
                this.sendHelpMessage(sender, 1);
            }

            return true;
        }

        String subCommand = args[0];
        String args1 = args.length > 1 ? args[1] : null;

        List<CommandWrapper> wrappers = this.getCommandWrappers(subCommand, args1);
        if (wrappers.isEmpty()) {
            this.sendHelpMessage(sender, 1);
            return true;
        }

        boolean invalidPerms = false;
        for (CommandWrapper wrapper : wrappers) {
            if (wrapper != null) {
                int index = 1;

                if (!Arrays.asList(wrapper.getCommand().subCommands()).isEmpty()) {
                    index++;
                }

                CompletableFuture<CommandResult> resultFuture = this.runCommand(sender, wrapper, Arrays.copyOfRange(args, index, args.length));
                if (resultFuture.isDone()) {
                    CommandResult result = resultFuture.join();
                    switch (result) {
                        case NO_PERMISSIONS -> {
                            invalidPerms = true;
                        }
                        case INCOMPATIBLE_METHOD -> {
                            // Ignore
                        }
                        case COMMAND_ERROR -> {
                            Messages.UNKNOWN_ERROR.send(sender);
                            return true;
                        }
                        case SUCCESS, COMMAND_ERROR_HANDLED -> {
                            return true;
                        }
                    }
                } else {
                    // Assume that if we are waiting, we are executing
                    // a command. Attempting to go over other command
                    // wrappers by this point will be needlessly expensive
                    // and issues caught here will mean there's a problem in
                    // the command system.
                    return true;
                }
            }
        }

        if (invalidPerms) {
            Messages.NO_PERMISSION.send(sender);
            return true;
        }

        this.sendUsageMessage(sender, wrappers.get(0).method);
        return true;
    }

    private void registerCommands() {
        for (Method method : MethodUtils.getMethodsWithAnnotation(this.getClass(), ArenaCommand.class, true, true)) {
            ArenaCommand arenaCommand = method.getAnnotation(ArenaCommand.class);

            CommandWrapper wrapper = new CommandWrapper(this, method, this.getUsage(method));
            for (String cmd : arenaCommand.commands()) {
                Set<CommandWrapper> wrappers = this.commandWrappers.getOrDefault(cmd, new HashSet<>());
                wrappers.add(wrapper);

                this.commandWrappers.put(cmd, wrappers);
            }
        }
    }

    public void injectWrapper(CommandWrapper wrapper) {
        for (String cmd : wrapper.getCommand().commands()) {
            Set<CommandWrapper> wrappers = this.commandWrappers.getOrDefault(cmd, new HashSet<>());
            wrappers.add(wrapper);

            this.commandWrappers.put(cmd, wrappers);
        }
    }

    public void injectExecutor(SubCommandExecutor executor) {
        this.subCommandExecutors.add(executor);
    }

    private CommandResult canRunCommand(CommandSender sender, CommandWrapper wrapper, String[] args) {
        ArenaCommand arenaCommand = wrapper.getCommand();

        try {
            if (arenaCommand.requiresOp() && !sender.isOp()) {
                return CommandResult.NO_PERMISSIONS;
            }

            if (!arenaCommand.permissionNode().isEmpty() && !this.hasPermission(sender, this.getPermissionNode(arenaCommand.permissionNode()))) {
                return CommandResult.NO_PERMISSIONS;
            }

            Method method = wrapper.method;
            Class<?>[] requestedParams = method.getParameterTypes();
            int argCount = args.length;

            if (!(sender instanceof Player) && requestedParams[0].equals(Player.class)) {
                return CommandResult.INCOMPATIBLE_METHOD;
            }

            if (requestedParams[requestedParams.length - 1].equals(String[].class)) {
                argCount = requestedParams.length - 2;
                int varParamCount = args.length - argCount;

                if (arenaCommand.minArgs() > varParamCount) {
                    return CommandResult.INCOMPATIBLE_METHOD;
                }

                if (arenaCommand.maxArgs() != -1 && arenaCommand.maxArgs() < varParamCount) {
                    return CommandResult.INCOMPATIBLE_METHOD;
                }
            } else if (requestedParams.length - 1 != argCount) {
                return CommandResult.INCOMPATIBLE_METHOD;
            }

            return CommandResult.SUCCESS;
        } catch (Exception e) {
            BattleArena.getInstance().error("An error occurred while executing command", e);
            return CommandResult.COMMAND_ERROR;
        }
    }

    private CompletableFuture<CommandResult> runCommand(CommandSender sender, CommandWrapper wrapper, String[] args) {
        try {
            CommandResult canRun = this.canRunCommand(sender, wrapper, args);
            if (canRun != CommandResult.SUCCESS) {
                return CompletableFuture.completedFuture(canRun);
            }

            Method method = wrapper.method;
            Class<?>[] requestedParams = method.getParameterTypes();
            Object[] params = new Object[requestedParams.length];
            int argCount = args.length;

            params[0] = sender;

            if (requestedParams[requestedParams.length - 1].equals(String[].class)) {
                argCount = requestedParams.length - 2;
                int varParamCount = args.length - argCount;

                String[] varParams = varParamCount == 0 ? new String[0] : Arrays.copyOfRange(args, argCount, args.length);
                params[params.length - 1] = varParams;
            }

            boolean sentInvalidArgument = false;
            boolean commandFound = true;
            for (int i = 0; i < argCount; i++) {
                Object obj = this.verifyArgument(sender, args[i], requestedParams[i + 1]);

                if (obj == null) {
                    commandFound = false;

                    boolean invalidArgument = this.invalidArgument(sender, requestedParams[i + 1], args[i]);
                    if (invalidArgument) {
                        sentInvalidArgument = true;
                        break;
                    }

                    break;
                } else {
                    params[i + 1] = obj;
                }
            }

            if (sentInvalidArgument) {
                return CompletableFuture.completedFuture(CommandResult.COMMAND_ERROR_HANDLED);
            }

            if (commandFound) {
                Object[] completedParams = new Object[params.length];
                // Check to see if any of our parameters are futures
                List<CompletableFuture<?>> futures = new ArrayList<>();
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    if (param instanceof CompletableFuture<?> future) {
                        futures.add(future);

                        int paramIdx = i;
                        future.thenAccept(result -> completedParams[paramIdx] = result);
                    } else {
                        completedParams[i] = param;
                    }
                }

                if (futures.isEmpty()) {
                    return CompletableFuture.completedFuture(this.invokeCommand(method, wrapper, params));
                }

                // Wait for all futures to complete
                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .thenApply(result -> this.invokeCommand(method, wrapper, completedParams));
            }
        } catch (Exception e) {
            BattleArena.getInstance().error("An error occurred while executing command", e);
            return CompletableFuture.completedFuture(CommandResult.COMMAND_ERROR);
        }

        return CompletableFuture.completedFuture(CommandResult.COMMAND_ERROR);
    }

    private CommandResult invokeCommand(Method method, CommandWrapper wrapper, Object[] params) {
        try {
            Object result = method.invoke(wrapper.instance, params);
            if (result instanceof CommandResult commandResult) {
                return commandResult;
            }

            if (result instanceof Boolean bool) {
                if (bool) {
                    return CommandResult.SUCCESS;
                } else {
                    return CommandResult.FAILURE;
                }
            }
        } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            BattleArena.getInstance().error("An error occurred while executing command", e);
            return CommandResult.COMMAND_ERROR;
        }

        // Assume success?
        return CommandResult.SUCCESS;
    }

    public void sendNoArgumentMessage(CommandSender sender) {
        this.sendHelpMessage(sender, 1);
    }

    public void sendHeader(CommandSender sender) {
        Messages.HEADER.sendCentered(sender, StringUtils.capitalize(this.parentCommand));
    }

    public void sendHelpMessage(CommandSender sender, int page) {
        // Compile all the command arguments
        Map<String, CommandWrapper> commandWrappers = new HashMap<>();
        for (Map.Entry<String, Set<CommandWrapper>> entry : this.commandWrappers.entrySet()) {
            for (CommandWrapper wrapper : entry.getValue()) {
                commandWrappers.put(wrapper.usage, wrapper);
            }
        }

        // Sort alphabetically and filter out commands that the sender doesn't have permission for
        List<CommandWrapper> commands = new ArrayList<>(commandWrappers.values()).stream().filter(wrapper -> {
            ArenaCommand arenaCommand = wrapper.getCommand();
            return arenaCommand.permissionNode().isEmpty() || this.hasPermission(sender, this.getPermissionNode(arenaCommand.permissionNode()));
        }).sorted(Comparator.comparing(wrapper -> wrapper.usage)).toList();

        // Player has no permissions to view any commands
        if (commands.isEmpty()) {
            Messages.NO_PERMISSION.send(sender);
            return;
        }

        this.sendHeader(sender);

        // Now send a certain page
        int maxPages = (int) Math.ceil(commands.size() / (double) COMMANDS_PER_PAGE);
        if (page > maxPages) {
            page = maxPages;
        }

        if (page < 1) {
            page = 1;
        }

        int startIndex = (page - 1) * COMMANDS_PER_PAGE;
        int endIndex = Math.min(startIndex + COMMANDS_PER_PAGE, commands.size());
        for (int i = startIndex; i < endIndex; i++) {
            CommandWrapper wrapper = commands.get(i);
            if (wrapper == null) {
                continue;
            }

            ArenaCommand arenaCommand = wrapper.getCommand();
            String command = "/" + this.parentCommand + " " + (arenaCommand.commands().length > 0 ? arenaCommand.commands()[0] : "");
            if (arenaCommand.subCommands().length > 0) {
                command += " " + arenaCommand.subCommands()[0];
            }

            HoverEvent<Component> hoverEvent = HoverEvent.showText(Messages.CLICK_TO_PREPARE.toComponent(command));
            ClickEvent clickEvent = ClickEvent.suggestCommand(command);
            sender.sendMessage(
                    Component.text("/" + this.parentCommand + " " + wrapper.usage, Messages.PRIMARY_COLOR)
                            .append(Component.text(wrapper.getCommand().description(), Messages.SECONDARY_COLOR))
                            .clickEvent(clickEvent)
                            .hoverEvent(hoverEvent)
            );
        }

        TextComponent.Builder rootComponent = Component.text();

        Component pageMessage = Component.text("Page " + page + "/" + maxPages, NamedTextColor.WHITE, TextDecoration.BOLD);
        if (page > 1) {
            rootComponent.append(Component.text("«     ", Messages.PRIMARY_COLOR)
                    .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/" + this.parentCommand + " help " + (page - 1)))
                    .hoverEvent(Component.text("Click to go to the previous page.", Messages.SECONDARY_COLOR))
            );
        } else {
            rootComponent.append(Component.text("      "));
        }

        rootComponent.append(pageMessage);

        if (page < maxPages) {
            rootComponent.append(Component.text("     »", Messages.PRIMARY_COLOR)
                    .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/" + this.parentCommand + " help " + (page + 1)))
                    .hoverEvent(Component.text("Click to go to the next page.", Messages.SECONDARY_COLOR))
            );
        } else {
            rootComponent.append(Component.text("      "));
        }

        sender.sendMessage(PaginationCalculator.center(rootComponent.build(), Component.space()));
    }

    public void sendUsageMessage(CommandSender sender, Method method) {
        Messages.COMMAND_USAGE.send(sender, "/" + this.parentCommand + " " + getUsage(method).trim());
    }

    private List<CommandWrapper> getCommandWrappers(String command, String subCommand) {
        List<CommandWrapper> genericWrappers = new ArrayList<>();
        List<CommandWrapper> explicitWrappers = new ArrayList<>();
    
        for (String cmd : this.commandWrappers.keySet()) {
            for (CommandWrapper wrapper : this.commandWrappers.get(cmd)) {
                ArenaCommand arenaCommand = wrapper.getCommand();
    
                if (!Arrays.asList(arenaCommand.commands()).contains(command)) {
                    continue;
                }
    
                List<String> subCommands = Arrays.asList(arenaCommand.subCommands());
                if (subCommands.contains(subCommand)) {
                    explicitWrappers.add(wrapper);
                } else if (subCommands.isEmpty()) {
                    genericWrappers.add(wrapper);
                }
            }
        }
    
        return explicitWrappers.isEmpty() ? genericWrappers : explicitWrappers;
    }

    private Object verifyArgument(CommandSender sender, String arg, Class<?> parameter) {
        Object customArg = this.onVerifyArgument(sender, arg, parameter);
        if (customArg != null) {
            return customArg;
        }

        switch (parameter.getSimpleName().toLowerCase()) {
            case "string" -> {
                return arg;
            }
            case "int" -> {
                return Integer.parseInt(arg);
            }
            case "double" -> {
                return Double.parseDouble(arg);
            }
            case "float" -> {
                return Float.parseFloat(arg);
            }
            case "boolean" -> {
                return switch (arg) {
                    case "true", "yes", "on" -> true;
                    case "false", "no", "off" -> false;
                    default -> null;
                };
            }
            case "duration" -> {
                try {
                    return DurationParser.deserializeSingular(arg);
                } catch (ParseException e) {
                    ParseException.handle(e);
                    return null;
                }
            }
            case "position" -> {
                String[] coords = arg.split(",");
                if (coords.length != 3) {
                    return null;
                }

                double x = Double.parseDouble(coords[0]);
                double y = Double.parseDouble(coords[1]);
                double z = Double.parseDouble(coords[2]);

                return Position.fine(x, y, z);
            }
            case "blockposition" -> {
                String[] coords = arg.split(",");
                if (coords.length != 3) {
                    return null;
                }

                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                int z = Integer.parseInt(coords[2]);

                return Position.block(x, y, z);
            }
            case "material" -> {
                try {
                    return ItemStackParser.deserializeSingular(arg);
                } catch (ParseException e) {
                    ParseException.handle(e);
                    return null;
                }
            }
            case "player" -> {
                return Bukkit.getPlayer(arg);
            }
            case "offlineplayer" -> {
                return Bukkit.getOfflinePlayer(arg);
            }
            case "world" -> {
                return Bukkit.getWorld(arg);
            }
            case "arena" -> {
                return BattleArena.getInstance().getArena(arg);
            }
            default -> {
                for (SubCommandExecutor subCommandExecutor : this.subCommandExecutors) {
                    Object obj = subCommandExecutor.onVerifyArgument(sender, arg, parameter);
                    if (obj != null) {
                        return obj;
                    }
                }

                if (parameter.isEnum()) {
                    try {
                        return Enum.valueOf((Class<Enum>) parameter, arg.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                }

                return null;
            }
        }
    }

    protected Object onVerifyArgument(CommandSender sender, String arg, Class<?> parameter) {
        return null;
    }

    protected boolean invalidArgument(CommandSender sender, Class<?> parameter, String input) {
        boolean invalidArgument = this.onInvalidArgument(sender, parameter, input);
        if (invalidArgument) {
            return true;
        }

        switch (parameter.getSimpleName().toLowerCase()) {
            case "string", "int", "double", "float", "boolean" -> {
                Messages.INVALID_TYPE.send(sender, input, parameter.getSimpleName().toLowerCase(Locale.ROOT));
                return true;
            }
            case "player", "offlineplayer" -> {
                Messages.PLAYER_NOT_ONLINE.send(sender, input);
                return true;
            }
            case "arena" -> {
                Messages.ARENA_DOES_NOT_EXIST.send(sender, input);
                return true;
            }
            case "position", "blockposition" -> {
                Messages.INVALID_POSITION.send(sender, input);
                return true;
            }
        }

        return false;
    }

    protected boolean onInvalidArgument(CommandSender sender, Class<?> parameter, String input) {
        for (SubCommandExecutor subCommandExecutor : this.subCommandExecutors) {
            boolean success = subCommandExecutor.onInvalidArgument(sender, parameter, input);
            if (success) {
                return true;
            }
        }

        return false;
    }

    private List<String> verifyTabComplete(String arg, Class<?> parameter) {
        List<String> completions = switch (parameter.getSimpleName().toLowerCase()) {
            case "material" -> Arrays.stream(Material.values()).map(Material::name).collect(Collectors.toList());
            case "player" -> Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
            case "offlineplayer" ->
                // lol no way we're listing all offline players
                Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
            case "world" -> Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
            case "arena" -> BattleArena.getInstance().getArenas().stream().map(Arena::getName).collect(Collectors.toList());
            default -> new ArrayList<>();
        };

        List<String> customCompletions = this.onVerifyTabComplete(arg, parameter);
        if (customCompletions != null && !customCompletions.isEmpty()) {
            completions.addAll(customCompletions);
        }

        for (SubCommandExecutor subCommandExecutor : this.subCommandExecutors) {
            List<String> subCompletions = subCommandExecutor.onVerifyTabComplete(arg, parameter);
            if (subCompletions != null && !subCompletions.isEmpty()) {
                completions.addAll(subCompletions);
            }
        }

        for (int i = 0; i < completions.size(); i++) {
            String completion = completions.get(i);
            if (!completion.toLowerCase().startsWith(arg.toLowerCase())) {
                completions.remove(completion);
                i--;
            }
        }

        if (completions.isEmpty() && parameter.isEnum()) {
            completions.addAll(Arrays.stream(parameter.getEnumConstants())
                    .map(obj -> obj.toString().toLowerCase(Locale.ROOT))
                    .toList()
            );
        }

        return completions;
    }

    protected List<String> onVerifyTabComplete(String arg, Class<?> parameter) {
        return null;
    }

    public String getUsage(Method method) {
        ArenaCommand arenaCommand = method.getAnnotation(ArenaCommand.class);
        StringBuilder builder = new StringBuilder(arenaCommand.commands().length > 0 ? arenaCommand.commands()[0] + " " : "");
        int index = 1;

        if (arenaCommand.subCommands().length > 0) {
            builder.append(arenaCommand.subCommands()[0]).append(" ");
        }

        Parameter[] requestedParams = method.getParameters();
        for (int i = index; i < requestedParams.length; i++) {
            Parameter parameter = requestedParams[i];
            Argument argument = parameter.getAnnotation(Argument.class);
            builder.append(this.getUsageString(parameter.getType(), argument));
        }

        return builder.toString();
    }

    private String getUsageString(Class<?> parameter, @Nullable Argument argument) {
        if (argument != null) {
            if (parameter.isArray()) {
                return "[" + argument.name() + "...] ";
            }

            return "<" + argument.name() + "> ";
        }

        String usageString = this.onGetUsageString(parameter);
        if (usageString != null) {
            return usageString;
        }

        return switch (parameter.getSimpleName().toLowerCase()) {
            case "string[]" -> "[string...] ";
            case "int", "double", "float" -> "<number> ";
            case "duration" -> "<duration> ";
            case "boolean" -> "<true|false> ";
            case "material" -> "<material> ";
            case "player", "offlineplayer" -> "<player> ";
            case "world" -> "<world> ";
            case "arena" -> "<arena> ";
            case "position", "blockposition" -> "<x,y,z> ";
            default -> {
                for (SubCommandExecutor subCommandExecutor : this.subCommandExecutors) {
                    String usage = subCommandExecutor.getUsageString(parameter);
                    if (usage != null) {
                        yield usage;
                    }
                }

                if (parameter.isEnum()) {
                    if (parameter.getEnumConstants().length < 5) {
                        yield "<" + Arrays.stream(parameter.getEnumConstants())
                                .map(obj -> obj.toString().toLowerCase(Locale.ROOT))
                                .collect(Collectors.joining("|")) + "> ";
                    }

                    yield "<" + parameter.getSimpleName().toLowerCase(Locale.ROOT) + "> ";
                }

                yield "<string> ";
            }
        };
    }

    protected String onGetUsageString(Class<?> parameter) {
        return null;
    }

    @NotNull
    @Override
    public final List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        try {
            if (args.length == 1) {
                List<String> commands = new ArrayList<>();
                for (String cmd : this.commandWrappers.keySet()) {
                    CommandWrapper wrapper = this.commandWrappers.get(cmd).iterator().next();
                    ArenaCommand arenaCommand = wrapper.getCommand();

                    if (!arenaCommand.permissionNode().isEmpty() && !this.hasPermission(sender, this.getPermissionNode(arenaCommand.permissionNode()))) {
                        continue;
                    }

                    if (arenaCommand.requiresOp() && !sender.isOp()) {
                        continue;
                    }

                    commands.add(cmd);
                }

                StringUtil.copyPartialMatches(args[0], commands, completions);
            }

            if (args.length > 1) {
                Set<CommandWrapper> wrappers = this.commandWrappers.get(args[0]);
                for (CommandWrapper wrapper : wrappers) {
                    ArenaCommand arenaCommand = wrapper.getCommand();
                    if (!arenaCommand.permissionNode().isEmpty() && !this.hasPermission(sender, this.getPermissionNode(arenaCommand.permissionNode()))) {
                        continue;
                    }

                    boolean hasSubCommand = arenaCommand.subCommands().length > 0;
                    if (hasSubCommand && args.length == 2) {
                        StringUtil.copyPartialMatches(args[1], Arrays.asList(arenaCommand.subCommands()), completions);
                        continue;
                    }

                    Class<?>[] requestedParams = wrapper.method.getParameterTypes();
                    if (requestedParams.length < (hasSubCommand ? args.length - 1 : args.length)) {
                        continue;
                    }

                    Class<?> requestedParam;
                    String token = args[args.length - 1];
                    if (hasSubCommand && Arrays.asList(arenaCommand.subCommands()).contains(args[1])) {
                        requestedParam = requestedParams[args.length - 2];
                    } else {
                        requestedParam = requestedParams[args.length - 1];
                    }

                    List<String> subCompletions = new ArrayList<>(this.verifyTabComplete(token, requestedParam));
                    StringUtil.copyPartialMatches(token, subCompletions, completions);
                }
            }
        } catch (Exception e) {
            // TODO: Try and get this exception to not throw at all if possible?
        }

        return completions;
    }

    public Map<String, Set<CommandWrapper>> getCommandWrappers() {
        return this.commandWrappers;
    }

    public String getPermissionNode(String node) {
        return "battlearena.command." + (this.permissionSubNode == null ? "" : this.permissionSubNode + ".") + node;
    }

    protected boolean hasPermission(CommandSender sender, String permission) {
        return sender.isOp() || sender.hasPermission(permission);
    }

    public enum CommandResult {
        NO_PERMISSIONS,
        COMMAND_ERROR,
        COMMAND_ERROR_HANDLED,
        INCOMPATIBLE_METHOD,
        SUCCESS,
        FAILURE
    }

    public static class CommandWrapper {
        protected final Object instance;
        protected final Method method;
        protected final String usage;

        public CommandWrapper(Object instance, Method method, String usage) {
            this.instance = instance;
            this.method = method;
            this.usage = usage;
        }

        public ArenaCommand getCommand() {
            return this.method.getAnnotation(ArenaCommand.class);
        }
    }
}
