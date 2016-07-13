package com.github.mtakelemen.hellodocker.build;

import groovy.lang.Closure;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.gradle.api.Project;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public final class DockerExecutor {
    private static final String DOCKER_MACHINE = "docker-machine";
    private static final String DOCKER = "docker";

    private final Project project;
    private final String dockerMachineName;
    private final CommandExecutor commandExecutorSilent;
    private final CommandExecutor commandExecutor;
    private final AtomicReference<Map<String, String>> allEnvVarsRef;
    private final AtomicReference<Map<String, String>> envVarsRef;
    private final AtomicReference<String> dockerHostIpRef;
    private final AtomicBoolean startedDockerMachine;

    public DockerExecutor(Project project, String dockerMachineName) {
        this.project = project;
        this.dockerMachineName = dockerMachineName;
        this.commandExecutor = new CommandExecutor(project);
        this.commandExecutorSilent = new CommandExecutor(project, false, true);
        this.envVarsRef = new AtomicReference<>(null);
        this.allEnvVarsRef = new AtomicReference<>(null);
        this.dockerHostIpRef = new AtomicReference<>(null);
        this.startedDockerMachine = new AtomicBoolean(false);
    }

    private void ensureDockerMachineStarted() {
        if (startedDockerMachine.compareAndSet(false, true)) {
            String status = executeDockerMachineCommandUnsafe("status", dockerMachineName);
            String normStatus = status.trim().toLowerCase(Locale.ROOT);
            if (normStatus.contains("stop")) {
                System.out.println("Starting Docker machine: " + dockerMachineName);
                executeDockerMachineCommandUnsafe("start", dockerMachineName);
                System.out.println("Started Docker machine: " + dockerMachineName);
            }
        }
    }

    private String executeCommandSilently(String... command) {
        try {
            return commandExecutorSilent.executeCommand(command);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private String executeDockerMachineCommandUnsafe(String... command) {
        return executeCommandSilently(array(DOCKER_MACHINE, command));
    }

    private String executeDockerMachineCommand(String... command) {
        ensureDockerMachineStarted();
        return executeDockerMachineCommandUnsafe(command);
    }

    public String getDockerHostIp() {
        String result = dockerHostIpRef.get();
        if (result == null) {
            result = executeDockerMachineCommand("ip", dockerMachineName).trim();

            if (!dockerHostIpRef.compareAndSet(null, result)) {
                result = dockerHostIpRef.get();
            }
        }
        return result;
    }

    public Map<String, String> getExtraEnvVars() {
        Map<String, String> result = envVarsRef.get();
        if (result == null) {
            String envVarScript = executeDockerMachineCommand("env", "--shell=sh/bash", dockerMachineName);

            result = parseExportShellScript(envVarScript);
            if (!envVarsRef.compareAndSet(null, result)) {
                result = envVarsRef.get();
            }
        }
        return result;
    }

    public Map<String, String> getAllEnvVars() {
        Map<String, String> result = allEnvVarsRef.get();
        if (result == null) {
            result = new HashMap<>();
            result.putAll(System.getenv());
            result.putAll(getExtraEnvVars());

            if (!allEnvVarsRef.compareAndSet(null, result)) {
                result = allEnvVarsRef.get();
            }
        }
        return result;
    }

    public String executeDockerCommand(String... command) {
        return executeDockerCommand(commandExecutor, command);
    }

    public String executeSilentDockerCommand(String... command) {
        return executeDockerCommand(commandExecutorSilent, command);
    }

    public Object executeDockerCommandGetJson(String... command) {
        JSONParser parser = new JSONParser();
        String jsonStr = executeSilentDockerCommand(command);
        try {
            return parser.parse(jsonStr);
        } catch (ParseException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static String[] array(String first, String... last) {
        String[] result = new String[last.length + 1];
        result[0] = first;
        System.arraycopy(last, 0, result, 1, last.length);
        return result;
    }

    private String executeDockerCommand(CommandExecutor executor, String... command) {
        ensureDockerMachineStarted();

        Map<String, String> env = getAllEnvVars();
        try {
            return executor.executeCommand(env, array(DOCKER, command));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private List<String> getContainerIdsByContainerId(boolean all, Predicate<? super String> filter) {
        String output = executeSilentDockerCommand("ps", "-q",  "--no-trunc=true", (all ? "-a" : ""));

        List<String> result = new LinkedList<>();
        GroovyUtils.forEachLine(output, (String line) -> {
            String id = line.trim();
            if (filter == null || filter.test(id)) {
                result.add(id);
            }
        });
        return result;
    }

    public List<String> getContainerIdsByJsonConfig(boolean all, Closure<Boolean> filter) {
        return getContainerIdsByJsonConfig(all, (obj) -> filter.call(obj));
    }

    public List<String> getContainerIdsByJsonConfig(boolean all, Predicate<? super JSONObject> filter) {
        Predicate<String> idFilter;
        if (filter != null) {
            idFilter = (String id) -> {
                List<?> configObjs = (List<?>)executeDockerCommandGetJson("inspect", id);
                JSONObject configObj = (JSONObject)configObjs.get(0);
                return filter.test(configObj);
            };
        }
        else {
            idFilter = null;
        }

        return getContainerIdsByContainerId(all, idFilter);
    }

    public List<String> getContainerIdsByImageName(boolean all, Closure<Boolean> filter) {
        return getContainerIdsByImageName(all, (imageName) -> filter.call(imageName));
    }

    public List<String> getContainerIdsByImageName(boolean all, Predicate<? super String> imageNameFilter) {
        Predicate<? super JSONObject> nameFilter = null;
        if (imageNameFilter != null) {
            nameFilter = (JSONObject configObj) -> {
                JSONObject configRoot = (JSONObject)configObj.get("Config");
                Object imageRoot = configRoot != null ? configRoot.get("Image") : null;
                String imageName = imageRoot != null ? imageRoot.toString() : null;
                return imageName != null ? imageNameFilter.test(imageName) : false;
            };
        }
        return getContainerIdsByJsonConfig(all, nameFilter);
    }

    public List<String> getContainerIds(boolean all) {
        return getContainerIdsByContainerId(all, null);
    }

    private Map<String, String> parseExportShellScript(String content) {
        Map<String, String> envVars = new HashMap<>();
        GroovyUtils.forEachLine(content, (String line) -> {
            tryAddKeyValueFromExport(line, envVars);
        });
        return Collections.unmodifiableMap(envVars);
    }

    private String removeQuotes(String str) {
        if (str.startsWith("\"")) {
            return str.substring(1, str.length() - 1);
        }
        else {
            return str;
        }
    }

    private boolean tryAddKeyValueFromExport(String exportCommand, Map<String, String> resultContainer) {
        String normCommand = exportCommand.trim();
        String commandName = "export";
        if (normCommand.startsWith(commandName)) {
            String nameAndValue = normCommand.substring(commandName.length()).trim();
            int valueSepIndex = nameAndValue.indexOf('=');
            if (valueSepIndex < 0) {
                return false;
            }

            String key = nameAndValue.substring(0, valueSepIndex).trim();
            String value = removeQuotes(nameAndValue.substring(valueSepIndex + 1).trim());
            resultContainer.put(key, value);

            return true;
        }
        else {
            return false;
        }
    }
}
