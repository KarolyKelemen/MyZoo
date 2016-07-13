package com.github.mtakelemen.hellodocker.build;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.gradle.api.Project;

public class DockerPluginExtension {
    private final Project project;

    private int serverPort;
    private File imagePackageDir;

    private String dockerFileTemplateName;

    private String dockerImageGroup;
    private String dockerImageName;
    private String dockerImageVersion;
    private String dockerMachineName;

    private String ipSuffix;
    private String networkName;

    private final List<HostEntry> hostEntries;
    private final List<ContainerLinkDef> links;

    public DockerPluginExtension(Project project) {
        this.project = Objects.requireNonNull(project);
        this.serverPort = 9005;
        this.imagePackageDir = null;

        this.dockerImageGroup = null;
        this.dockerImageName = null;
        this.dockerImageVersion = null;
        this.dockerMachineName = null;
        this.ipSuffix = null;
        this.networkName = null;
        this.hostEntries = new LinkedList<>();
        this.links = new LinkedList<>();
        this.dockerFileTemplateName = "Dockerfile.template";
    }

    public Project getProject() {
        return project;
    }

    public String getDockerFileTemplateName() {
        return dockerFileTemplateName;
    }

    public void setDockerFileTemplateName(String dockerFileTemplateName) {
        this.dockerFileTemplateName = Objects.requireNonNull(dockerFileTemplateName);
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public void setImagePackageDir(File imagePackageDir) {
        this.imagePackageDir = imagePackageDir;
    }

    public File getImagePackageDir() {
        return imagePackageDir != null
                ? imagePackageDir
                : new File(project.getBuildDir(), "docker-pkg");
    }

    public String getCompleteDockerImageNamePrefix() {
        return getDockerImageGroup() + "/" + getDockerImageName() + ":";
    }

    public String getCompleteDockerImageName() {
        return getCompleteDockerImageNamePrefix() + getDockerImageVersion();
    }

    public String getDockerImageGroup() {
        return dockerImageGroup != null
                ? dockerImageGroup
                : project.getGroup().toString();
    }

    public void setDockerImageGroup(String dockerImageGroup) {
        this.dockerImageGroup = dockerImageGroup;
    }

    public String getDockerImageName() {
        return dockerImageName != null
                ? dockerImageName
                : project.getName().toLowerCase(Locale.ROOT); // TODO: Should be normalized better
    }

    public void setDockerImageName(String dockerImageName) {
        this.dockerImageName = dockerImageName;
    }

    public String getDockerImageVersion() {
        return dockerImageVersion != null
                ? dockerImageVersion
                : project.getVersion().toString();
    }

    public void setDockerImageVersion(String dockerImageVersion) {
        this.dockerImageVersion = dockerImageVersion;
    }

    private String getDefaultDockerMachineName() {
        return project.hasProperty("dockerMachineName")
                ? project.property("dockerMachineName").toString()
                : "default";
    }

    public String getDockerMachineName() {
        return dockerMachineName != null
                ? dockerMachineName
                : getDefaultDockerMachineName();
    }

    public void setDockerMachineName(String dockerMachineName) {
        this.dockerMachineName = dockerMachineName;
    }

    public String getIpSuffix() {
        return ipSuffix;
    }

    public void setIpSuffix(String ipSuffix) {
        this.ipSuffix = ipSuffix;
    }

    private String getDefaulNetworkName() {
        return project.hasProperty("dockerNetwork")
                ? project.property("dockerNetwork").toString()
                : null;
    }

    public String getNetworkName() {
        return networkName != null
                ? networkName
                : getDefaulNetworkName();
    }

    public void setNetworkName(String networkName) {
        this.networkName = networkName;
    }

    public void addHostEntry(String hostName, String ip) {
        addHostEntry(new HostEntry(hostName, ip));
    }

    public void addHostEntry(HostEntry entry) {
        hostEntries.add(Objects.requireNonNull(entry));
    }

    public List<HostEntry> getHostEntries() {
        return hostEntries;
    }

    public void addLink(String containerName, String alias) {
        addLink(new ContainerLinkDef(containerName, alias));
    }

    public void addLink(ContainerLinkDef entry) {
        links.add(Objects.requireNonNull(entry));
    }

    public List<ContainerLinkDef> getLinks() {
        return links;
    }
}
