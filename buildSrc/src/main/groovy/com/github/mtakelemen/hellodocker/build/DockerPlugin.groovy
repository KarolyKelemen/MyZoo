package com.github.mtakelemen.hellodocker.build

import java.util.concurrent.atomic.AtomicReference
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.json.simple.JSONObject

public class DockerPlugin implements Plugin<Project> {
    public DockerPlugin() {
    }

    @Override
    public void apply(Project project) {
        project.apply plugin: 'java'

        project.extensions.docker = new DockerPluginExtension(project);
        DockerTaskUtils.addDockerPluginTasks(project.docker)
    }
}
