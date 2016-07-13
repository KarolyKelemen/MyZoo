package com.github.mtakelemen.hellodocker.build;

import java.util.Objects;

public class ContainerLinkDef {
    private final String containerName;
    private final String alias;

    public ContainerLinkDef(String containerName, String alias) {
        this.containerName = Objects.requireNonNull(containerName);
        this.alias = Objects.requireNonNull(alias);
    }

    public String getContainerName() {
        return containerName;
    }

    public String getAlias() {
        return alias;
    }
}
