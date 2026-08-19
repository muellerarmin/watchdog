package com.example.dockerwatchdog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class DockerClient {
    private final ObjectMapper objectMapper;
    private final WatchdogProperties properties;

    DockerClient(ObjectMapper objectMapper, WatchdogProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public List<DockerContainer> listContainers() throws IOException, InterruptedException {
        Process process = new ProcessBuilder(properties.dockerCommand(), "ps", "--all", "--format", "{{json .}}")
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException(output.isBlank() ? "docker command failed with exit code " + exitCode : output.strip());
        }

        List<DockerContainer> containers = new ArrayList<>();
        for (String line : output.lines().filter(line -> !line.isBlank()).toList()) {
            JsonNode node = objectMapper.readTree(line);
            String state = node.path("State").asText("unknown");
            String status = node.path("Status").asText("").toLowerCase();
            String health = status.contains("(healthy)") ? "healthy"
                    : status.contains("(unhealthy)") ? "unhealthy"
                    : status.contains("(health: starting)") ? "starting" : null;
            containers.add(new DockerContainer(node.path("Names").asText(), state, health));
        }
        return containers;
    }
}
