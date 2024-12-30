package org.battleplugins.arena.module;

import org.battleplugins.arena.BattleArena;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ArenaModuleLoader {
    private final BattleArena plugin;
    private final ClassLoader classLoader;
    private final Path modulePath;

    private final Map<String, ArenaModuleContainer<?>> modules = new HashMap<>();
    private final Set<ModuleLoadException> failedModules = new HashSet<>();

    public ArenaModuleLoader(BattleArena plugin, ClassLoader classLoader, Path modulePath) {
        this.plugin = plugin;
        this.classLoader = classLoader;
        this.modulePath = modulePath;
    }

    public void loadModules() throws IOException {
        if (Files.notExists(this.modulePath)) {
            Files.createDirectories(this.modulePath);
        }

        try (Stream<Path> pathStream = Files.walk(this.modulePath)) {
            pathStream.filter(path -> path.getFileName().toString().endsWith(".jar") || path.getFileName().toString().endsWith(".zip"))
                    .forEach(path -> {
                        try (ZipFile zipFile = new ZipFile(path.toFile());
                             URLClassLoader classLoader = new URLClassLoader(
                                     new URL[] { path.toUri().toURL() },
                                     this.classLoader
                             )) {

                            AtomicReference<ArenaModule> arenaModuleRef = new AtomicReference<>();
                            AtomicReference<Class<?>> arenaModuleClassRef = new AtomicReference<>();
                            zipFile.stream().forEach((file -> {
                                if (file.getName().endsWith(".class")) {
                                    try (InputStream inputStream = zipFile.getInputStream(file)) {
                                        byte[] buffer = new byte[inputStream.available()];
                                        inputStream.read(buffer);

                                        String canonicalName = getClassCanonicalName(file);
                                        Class<?> clazz = classLoader.loadClass(canonicalName.substring(0, canonicalName.lastIndexOf(".")));

                                        // Check to see if the class is annotated with @ArenaModule
                                        if (clazz.isAnnotationPresent(ArenaModule.class)) {
                                            arenaModuleClassRef.set(clazz);

                                            // Set the annotation
                                            arenaModuleRef.set(clazz.getAnnotation(ArenaModule.class));
                                        }
                                    } catch (Throwable e) {
                                        // Ignore NoClassDefFoundError - this is typically thrown when
                                        // a module extends a class from a third party plugin which may not be
                                        // found when loading the module if the plugin is not present. The
                                        // above logic just looks for a class annotated with @ArenaModule
                                        // which will fail after this code runs anyway.
                                        if (e instanceof NoClassDefFoundError) {
                                            return;
                                        }

                                        this.plugin.error("Error when setting up module {}! Please contact the module author!", path.getFileName().toString(), e);

                                        // Add the exception to the failed modules set
                                        if (arenaModuleRef.get() != null) {
                                            this.failedModules.add(new ModuleLoadException(arenaModuleRef.get(), e));
                                        }
                                    }
                                }
                            }));

                            Class<?> moduleMainClass = arenaModuleClassRef.get();
                            ArenaModule arenaModule = moduleMainClass.getAnnotation(ArenaModule.class);
                            if (arenaModule == null) {
                                this.plugin.error("Module {} does not have a @ArenaModule annotation!", path.getFileName().toString());
                                return;
                            }

                            if (arenaModule.authors().length == 0) {
                                this.plugin.info("Loading module {} v{}", arenaModule.name(), arenaModule.version());
                            } else {
                                this.plugin.info("Loading module {} v{} by {}", arenaModule.name(), arenaModule.version(), String.join(", ", arenaModule.authors()));
                            }

                            Object mainClass = moduleMainClass.getConstructor().newInstance();
                            this.modules.put(arenaModule.id(), new ArenaModuleContainer<>(path, this, arenaModule, mainClass));
                        } catch (Throwable e) {
                            this.plugin.error("Failed to load module {}!", path.getFileName().toString(), e);
                        }
                    });
        }
    }

    public void enableModules() {
        this.modules.values().forEach(module -> {
            if (this.plugin.getMainConfig().getDisabledModules().contains(module.module().id())) {
                this.plugin.debug("Module {} is disabled in the configuration. Skipping...", module.module().name());
                return;
            }

            if (module.mainClass() instanceof ArenaModuleInitializer moduleInitializer) {
                Bukkit.getPluginManager().registerEvents(moduleInitializer, this.plugin);
            }

            this.plugin.info("Enabled module {} v{}", module.module().name(), module.module().version());
        });
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <T> ArenaModuleContainer<T> getModule(String id) {
        return (ArenaModuleContainer<T>) this.modules.get(id);
    }

    public List<ArenaModuleContainer<?>> getModules() {
        return List.copyOf(this.modules.values());
    }

    public Set<ModuleLoadException> getFailedModules() {
        return Set.copyOf(this.failedModules);
    }

    void disableModule(ModuleLoadException reason) {
        this.plugin.info("Disabling module {} for reason: {}", reason.getModule().name(), reason.getMessage());
        ArenaModuleContainer<?> module = this.modules.remove(reason.getModule().id());
        if (module != null && module.mainClass() instanceof ArenaModuleInitializer moduleInitializer) {
            HandlerList.unregisterAll(moduleInitializer);
        }

        this.failedModules.add(reason);
    }

    @Nullable
    InputStream getResource(Path path, String location) {
        try {
            ZipFile zipFile = new ZipFile(path.toFile());
            ZipEntry entry = zipFile.getEntry(location);
            if (entry == null) {
                return null;
            }

            return zipFile.getInputStream(entry);
        } catch (IOException e) {
            this.plugin.error("Failed to get resource {} from module {}!", location, path.getFileName().toString(), e);
            return null;
        }
    }

    private static String getClassCanonicalName(ZipEntry entry) {
        String entryName = entry.getName();
        if (getFileExtension(entryName).toLowerCase().endsWith("class")) {
            return entryName.replaceAll("/", ".");
        } else {
            return null;
        }
    }

    private static String getFileExtension(String fileName) {
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
}
