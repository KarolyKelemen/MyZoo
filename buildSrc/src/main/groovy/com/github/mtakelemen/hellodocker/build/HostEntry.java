package com.github.mtakelemen.hellodocker.build;

import java.util.Objects;

public final class HostEntry {
    private final String hostName;
    private final String ip;

    public HostEntry(String hostName, String ip) {
        this.hostName = Objects.requireNonNull(hostName);
        this.ip = Objects.requireNonNull(ip);
    }

    public String getHostName() {
        return hostName;
    }

    public String getIp() {
        return ip;
    }
}
