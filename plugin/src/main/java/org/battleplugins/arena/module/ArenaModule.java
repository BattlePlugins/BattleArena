package org.battleplugins.arena.module;

import org.battleplugins.arena.BattleArena;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Represents a module that can be loaded by {@link BattleArena}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ArenaModule {

    /**
     * The id of the module.
     *
     * @return the id of the module
     */
    String id();

    /**
     * The name of the module.
     *
     * @return the name of the module
     */
    String name();

    /**
     * The description of the module.
     *
     * @return the description of the module
     */
    String description() default "";

    /**
     * The version of the module.
     *
     * @return the version of the module
     */
    String version() default "1.0.0";

    /**
     * The authors of the module.
     *
     * @return the authors of the module
     */
    String[] authors() default {};

    /**
     * The priority of the module.
     *
     * @return the priority of the module
     */
    int priority() default 0;
}
