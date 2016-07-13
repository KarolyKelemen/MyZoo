package com.github.mtakelemen.hellodocker.build;

import groovy.lang.Closure;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;

public class BuildUtils {
    public static String getProjectProperty(Project project, String name, String defaultValue) {
        Object rawValue = project.hasProperty(name)
                ? project.property(name)
                : defaultValue;

        if (rawValue == null) {
            return defaultValue;
        }

        return rawValue.toString();
    }

    private static String configTaskName(String baseName) {
        if (baseName.isEmpty()) {
            throw new IllegalArgumentException("task name must not be empty");
        }

        char firstCh = Character.toUpperCase(baseName.charAt(0));
        return "configure" + firstCh + baseName.substring(1);
    }

    public static Task addDelayedConfigTask(Task task, Closure<?> configurationAction) {
        return addDelayedConfigTask(task, (configuredTask) -> {
            configurationAction.setDelegate(configuredTask);
            configurationAction.setResolveStrategy(Closure.DELEGATE_FIRST);
            configurationAction.call(configuredTask);
        });
    }

    public static Task addDelayedConfigTask(Task task, Action<? super Task> configurationAction) {
        Task result = task.getProject().task(configTaskName(task.getName()));
        result.doLast((configTask) -> {
            configurationAction.execute(task);
        });
        task.dependsOn(result);
        return result;
    }

    private static Task taskWithType(Project project, String name, Class<?> type) {
        return project.task(Collections.singletonMap("type", type), name);
    }

    public static <T extends Task> Task newTaskWithDelayedConfig(Project project, String taskName, Class<T> taskType, Action<? super T> configurationAction) {
        Task task = taskWithType(project, taskName, taskType);
        addDelayedConfigTask(task, (rawTask) -> {
            T safeTask = taskType.cast(rawTask);
            configurationAction.execute(safeTask);
        });
        return task;
    }

    public static List<String> getContainerIds(DockerExecutor executor, String imageNamePrefix, boolean all) {
        return executor.getContainerIdsByImageName(all, (String imageName) -> {
             return imageName.toLowerCase(Locale.ROOT).startsWith(imageNamePrefix);
        });
    }

    private BuildUtils() {
        throw new AssertionError();
    }
}
