package org.battleplugins.arena.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.battleplugins.arena.BattleArena;
import org.battleplugins.arena.config.ArenaOption;
import org.battleplugins.arena.messages.Messages;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class Util {
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    /**
     * Serializes a component to a legacy string.
     * <p>
     * This method is internal and should not be used by other plugins.
     *
     * @param component the component to serialize
     * @return the serialized component
     */
    @ApiStatus.Internal
    public static String serializeToLegacy(Component component) {
        return LEGACY_SERIALIZER.serialize(component);
    }

    /**
     * Deserializes a legacy string to a component.
     * <p>
     * This method is internal and should not be used by other plugins.
     *
     * @param legacy the legacy string to deserialize
     * @return the deserialized component
     */
    @ApiStatus.Internal
    public static Component deserializeFromLegacy(String legacy) {
        return LEGACY_SERIALIZER.deserialize(legacy);
    }

    public static String toTimeStringShort(Duration duration) {
        long seconds = duration.getSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        seconds = seconds % 60;

        if (hours == 0) {
            return String.format("%02d:%02d", minutes, seconds);
        }

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public static String toTimeString(Duration duration) {
        long seconds = duration.toSecondsPart();
        long minutes = duration.toMinutesPart();
        long hours = duration.toHoursPart();
        long days = duration.toDaysPart();

        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append(" ");
            if (days == 1) {
                builder.append(Messages.DAY.asPlainText());
            } else {
                builder.append(Messages.DAYS.asPlainText());
            }
        }

        if (hours > 0) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }

            builder.append(hours).append(" ");
            if (hours == 1) {
                builder.append(Messages.HOUR.asPlainText());
            } else {
                builder.append(Messages.HOURS.asPlainText());
            }
        }

        if (minutes > 0) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }

            builder.append(minutes).append(" ");
            if (minutes == 1) {
                builder.append(Messages.MINUTE.asPlainText());
            } else {
                builder.append(Messages.MINUTES.asPlainText());
            }
        }

        if (seconds > 0) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }

            builder.append(seconds).append(" ");
            if (seconds == 1) {
                builder.append(Messages.SECOND.asPlainText());
            } else {
                builder.append(Messages.SECONDS.asPlainText());
            }
        }

        return builder.toString();
    }

    public static String toUnitString(long amount, TimeUnit unit) {
        switch (unit) {
            case MILLISECONDS -> {
                if (amount == 1) {
                    return amount + " " + Messages.MILLISECOND.asPlainText();
                } else {
                    return amount + " " + Messages.MILLISECONDS.asPlainText();
                }
            }
            case SECONDS -> {
                if (amount == 1) {
                    return amount + " " + Messages.SECOND.asPlainText();
                } else {
                    return amount + " " + Messages.SECONDS.asPlainText();
                }
            }
            case MINUTES -> {
                if (amount == 1) {
                    return amount + " " + Messages.MINUTE.asPlainText();
                } else {
                    return amount + " " + Messages.MINUTES.asPlainText();
                }
            }

            case HOURS -> {
                if (amount == 1) {
                    return amount + " " + Messages.HOUR.asPlainText();
                } else {
                    return amount + " " + Messages.HOURS.asPlainText();
                }
            }
            case DAYS -> {
                if (amount == 1) {
                    return amount + " " + Messages.DAY.asPlainText();
                } else {
                    return amount + " " + Messages.DAYS.asPlainText();
                }
            }
        }

        // Realistically, we will only ever be using the values above
        return unit.name().toLowerCase(Locale.ROOT);
    }

    public static <T> void copyFields(T oldInstance, T newInstance) {
        for (Field field : oldInstance.getClass().getDeclaredFields()) {
            if (!field.isAnnotationPresent(ArenaOption.class)) {
                continue;
            }

            field.setAccessible(true);

            try {
                field.set(newInstance, field.get(oldInstance));
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to copy field " + field.getName(), e);
            }
        }
    }

    public static void copyDirectories(File jarFile, Path outputPath, String directory, String... ignoredFiles) {
        Path jarPath = jarFile.toPath();
        try {
            if (Files.notExists(outputPath)) {
                Files.createDirectories(outputPath);
            }

            try (FileSystem fileSystem = FileSystems.newFileSystem(jarPath, Map.of())) {
                Path root = fileSystem.getPath("/");
                Path directoryPath = root.resolve(directory);
                if (Files.notExists(directoryPath)) {
                    return;
                }

                try (Stream<Path> paths = Files.walk(directoryPath)) {
                    paths.forEach(path -> {
                        if (Files.isDirectory(path)) {
                            return;
                        }

                        Path relativePath = directoryPath.relativize(path);
                        Path targetPath = outputPath.resolve(relativePath.toString());
                        if (Files.exists(targetPath)) {
                            // Check hashes - if different, copy from jar
                            if (Arrays.equals(getHash(path), getHash(targetPath))) {
                                return;
                            }
                        }

                        for (String ignoredFile : ignoredFiles) {
                            if (relativePath.toString().startsWith(ignoredFile)) {
                                return;
                            }
                        }

                        try {
                            Files.createDirectories(targetPath.getParent());
                            Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            BattleArena.getInstance().error("Failed to copy module {}!", path.getFileName(), e);
                        }
                    });
                }
            }
        } catch (Exception e) {
            BattleArena.getInstance().error("Failed to copy modules from jar!", e);
        }
    }

    private static byte[] getHash(Path path) {
        byte[] sha256;

        try {
            sha256 = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        } catch (Exception e) {
            throw new RuntimeException("Could not calculate pack hash", e);
        }

        return sha256;
    }
}
