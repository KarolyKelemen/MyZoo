package com.github.mtakelemen.hellodocker.build;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.bundling.Jar;
import org.json.simple.JSONObject;

final class DockerTaskUtils {
    private final DockerPluginExtension docker;
    private final AtomicReference<DockerExecutor> executorRef;

    private DockerTaskUtils(DockerPluginExtension docker) {
        this.docker = Objects.requireNonNull(docker);
        this.executorRef = new AtomicReference<>(null);
    }

    public static void addDockerPluginTasks(DockerPluginExtension docker) {
        DockerTaskUtils utils = new DockerTaskUtils(docker);
        utils.addPluginTasks();
    }

    private DockerExecutor getExecutor() {
        DockerExecutor result = executorRef.get();
        if (result == null) {
            result = new DockerExecutor(docker.getProject(), docker.getDockerMachineName());
            if (!executorRef.compareAndSet(null, result)) {
                result = executorRef.get();
            }
        }
        return result;
    }

    private void addPluginTasks() {
        Project project = docker.getProject();

        BuildUtils.newTaskWithDelayedConfig(project, "dropPackage", Delete.class, (task) -> {
            task.delete(docker.getImagePackageDir());
        });

        Task preparePackage = project.task("preparePackage");
        preparePackage.dependsOn("dropPackage");
        preparePackage.doLast((task) -> {
            docker.getImagePackageDir().mkdirs();
        });

        Task copyPackageFiles = BuildUtils.newTaskWithDelayedConfig(project, "copyPackageFiles", Copy.class, (task) -> {
            Jar jar = (Jar)project.getTasks().findByName("jar");
            task.from(jar.getArchivePath());

            task.into(docker.getImagePackageDir());
        });
        copyPackageFiles.dependsOn("build");

        Task copyDocker = BuildUtils.newTaskWithDelayedConfig(project, "copyDocker", Copy.class, (task) -> {
            task.from(new File(project.file("DockerTemplate"), docker.getDockerFileTemplateName()));

            task.into(docker.getImagePackageDir());

            task.rename(GroovyUtils.toClosure((Object file) -> "Dockerfile"));

            // Do not use Collections.singletonMap because UnsupportedOperationException
            // will be thrown. Caller tries to modify the passed map?
            Map<String, Object> vars = new HashMap<>();
            vars.put("project", project);
            task.expand(vars);
        });

        Task packageTask = project.task("package");
        packageTask.dependsOn("preparePackage", "copyPackageFiles", "copyDocker");

        copyPackageFiles.mustRunAfter(preparePackage);
        copyDocker.mustRunAfter(preparePackage);

        Task stopAndRemoveDockerContainer = project.task("stopAndRemoveDockerContainer");
        stopAndRemoveDockerContainer.doLast((task) -> {
            DockerExecutor dockerExecutor = getExecutor();

            String imageNamePrefix = docker.getCompleteDockerImageNamePrefix();

            BuildUtils.getContainerIds(dockerExecutor, imageNamePrefix, false).forEach((String id) -> {
                String projectName = project.getName();

                System.out.println("Stopping " + projectName + " container: " + id);
                dockerExecutor.executeSilentDockerCommand("stop", id);
                dockerExecutor.executeSilentDockerCommand("wait", id);
                System.out.println(projectName + " container has been stopped successfully: " + id);
            });

            BuildUtils.getContainerIds(dockerExecutor, imageNamePrefix, true).forEach((String id) -> {
                String projectName = project.getName();

                System.out.println("Removing " + projectName + " container: " + id);
                dockerExecutor.executeSilentDockerCommand("rm", id);
                System.out.println(projectName + " container has been removed successfully: " + id);
            });
        });

        Task removeDockerImage = project.task("removeDockerImage");
        removeDockerImage.dependsOn(stopAndRemoveDockerContainer);
        removeDockerImage.doLast((task) -> {
            DockerExecutor dockerExecutor = getExecutor();
            String dockerImageName = docker.getCompleteDockerImageName();

            try {
                dockerExecutor.executeDockerCommand("rmi", dockerImageName);
            } catch (IllegalStateException ex) {
                System.out.println("Image cannot be deleted: " + dockerImageName);
            }
        });

        Task dockerBuild = project.task("dockerBuild");
        dockerBuild.dependsOn(packageTask, removeDockerImage);
        dockerBuild.doLast((task) -> {
            DockerExecutor dockerExecutor = getExecutor();
            String dockerImageName = docker.getCompleteDockerImageName();
            dockerExecutor.executeDockerCommand("build", "-t", dockerImageName, docker.getImagePackageDir().toString());
        });

        Task installIntoDocker = project.task("installIntoDocker");
        installIntoDocker.dependsOn(dockerBuild);
        installIntoDocker.doLast((task) -> runImage());
    }

    private void runImage() {
        DockerExecutor dockerExecutor = getExecutor();

        String dockerImageName = docker.getCompleteDockerImageName();
        int serverPort = docker.getServerPort();

        List<String> cmd = new LinkedList<>();
        cmd.add("run");

        cmd.add("-d");

        cmd.add("--name");
        cmd.add(docker.getProject().getName());

        cmd.add("-p");
        cmd.add(serverPort + ":" + serverPort);

        for (HostEntry hostEntry: docker.getHostEntries()) {
            cmd.add("--add-host");
            cmd.add(hostEntry.getHostName() + ':' + hostEntry.getIp());
        }

        String networkName = docker.getNetworkName();
        if (networkName != null) {
            cmd.add("--net");
            cmd.add(networkName);

            String ip = tryGetIp(networkName);
            if (ip != null) {
                cmd.add("--ip");
                cmd.add(ip);
            }
        }

        for (ContainerLinkDef linkDef: docker.getLinks()) {
            cmd.add("--link");
            cmd.add(linkDef.getContainerName() + ':' + linkDef.getAlias());
        }

        cmd.add(dockerImageName);

        dockerExecutor.executeDockerCommand(cmd.toArray(new String[0]));
    }

    private static Object tryGetSubObj(Object obj, String... path) {
        Object result = obj;

        if (!(obj instanceof JSONObject)) {
            return path.length > 0 ? null : obj;
        }

        JSONObject currentObj = (JSONObject)obj;
        for (String pathEntry: path) {
            result = currentObj.get(pathEntry);
            if (result instanceof JSONObject) {
                currentObj = (JSONObject)result;
            }
            else {
                return null;
            }
        }
        return result;
    }

    private String tryGetIp(String networkName) {
        String ipSuffix = docker.getIpSuffix();
        if (ipSuffix == null) {
            return null;
        }

        JSONObject networkObj = tryGetNetworkDescr(networkName);
        if (networkObj == null) {
            System.out.println("Docker network not found: " + networkName);
            return null;
        }

        List<?> networkConfigs = (List<?>)tryGetSubObj(networkObj, "IPAM", "Config");
        if (networkConfigs == null || networkConfigs.isEmpty()) {
            System.out.println("Docker network has no config: " + networkName);
            return null;
        }

        Object firstConfig = networkConfigs.get(0);
        Object subnetObj = tryGetSubObj(firstConfig, "Subnet");
        String subnet = subnetObj != null ? subnetObj.toString() : "";

        return getIpForSubnet(subnet, ipSuffix);
    }

    private static String getIpForSubnet(String subnet, String ipSuffix) {
        int maskSepIndex = subnet.lastIndexOf('/');
        String baseIp = maskSepIndex >= 0 ? subnet.substring(0, maskSepIndex) : subnet;

        String dotPattern = Pattern.quote(".");

        String[] subnetParts = baseIp.split(dotPattern);
        String[] ipParts = ipSuffix.split(dotPattern);

        List<String> resultParts = new LinkedList<>();
        for (String ipPart: ipParts) {
            String normIpPart = ipPart.trim();
            if (!normIpPart.isEmpty()) {
                resultParts.add(normIpPart);
            }
        }

        for (int i = Math.min(3 - resultParts.size(), subnetParts.length - 1); i >= 0; i--) {
            String normPart = subnetParts[i].trim();
            resultParts.add(0, normPart);
        }

        return GroovyUtils.join(resultParts, ".");
    }

    private JSONObject tryGetNetworkDescr(String networkName) {
        DockerExecutor dockerExecutor = getExecutor();
        List<?> networks = (List<?>)dockerExecutor.executeDockerCommandGetJson("network", "inspect", networkName);
        for (Object networkObj: networks) {
            if (Objects.equals(tryGetSubObj(networkObj, "Name"), networkName)) {
                return (JSONObject)networkObj;
            }
        }
        return null;
    }
}
