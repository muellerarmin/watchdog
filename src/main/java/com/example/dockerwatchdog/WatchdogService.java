package com.example.dockerwatchdog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WatchdogService {
    private static final Logger log = LoggerFactory.getLogger(WatchdogService.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss z");
    private final DockerClient dockerClient;
    private final JavaMailSender mailSender;
    private final WatchdogProperties properties;
    private Map<String, String> previousStatus = Map.of();
    private String previousDockerProblem;

    WatchdogService(DockerClient dockerClient, JavaMailSender mailSender, WatchdogProperties properties) {
        this.dockerClient = dockerClient;
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${watchdog.check-interval-ms}")
    public synchronized void checkStatus() {
        StatusSnapshot snapshot = currentStatus();
        if (!snapshot.dockerAvailable()) {
            if (!snapshot.dockerProblem().equals(previousDockerProblem)) {
                send("Container status changed", "Docker status change detected at " + timestamp() + ".\n\nDocker daemon: " + snapshot.dockerProblem());
            }
            previousDockerProblem = snapshot.dockerProblem();
            return;
        }

        Map<String, String> current = snapshot.containers();
        List<String> changes = new java.util.ArrayList<>();
        if (previousDockerProblem != null) {
            changes.add("Docker daemon: recovered");
            previousDockerProblem = null;
        }
        current.forEach((name, problem) -> {
            String previous = previousStatus.get(name);
            if (problem != null && !problem.equals(previous)) {
                changes.add(name + ": " + problem);
            } else if (problem == null && previous != null) {
                changes.add(name + ": recovered");
            }
        });
        previousStatus.forEach((name, problem) -> {
            if (problem != null && !current.containsKey(name)) {
                changes.add(name + ": recovered");
            }
        });
        if (!changes.isEmpty()) {
            send("Container status changed", "Docker status change detected at " + timestamp() + ".\n\n" + String.join("\n", changes));
        }
        previousStatus = current;
    }

    @Scheduled(cron = "${watchdog.report-cron}", zone = "${watchdog.report-timezone}")
    public synchronized void weeklyReport() {
        StatusSnapshot snapshot = currentStatus();
        Map<String, String> current = snapshot.dockerAvailable()
                ? snapshot.containers()
                : Map.of("Docker daemon", snapshot.dockerProblem());
        String lines = current.isEmpty() ? "No containers matched the configured selection."
                : current.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + (entry.getValue() == null ? "healthy/running" : entry.getValue()))
                .collect(Collectors.joining("\n"));
        send("Weekly status report", "Weekly Docker status report generated at " + timestamp() + ".\n\n" + lines);
    }

    private StatusSnapshot currentStatus() {
        try {
            List<DockerContainer> containers = dockerClient.listContainers();
            Map<String, DockerContainer> byName = containers.stream()
                    .collect(Collectors.toMap(DockerContainer::name, container -> container));
            Map<String, String> result = new LinkedHashMap<>();
            if (properties.containers().size() == 1 && "all".equalsIgnoreCase(properties.containers().getFirst())) {
                containers.forEach(container -> result.put(container.name(), container.problem()));
            } else {
                properties.containers().forEach(name -> {
                    DockerContainer container = byName.get(name);
                    result.put(name, container == null ? "not found" : container.problem());
                });
            }
            return new StatusSnapshot(true, result, null);
        } catch (Exception exception) {
            log.error("Docker check failed", exception);
            return new StatusSnapshot(false, Map.of(), "unavailable: " + exception.getMessage());
        }
    }

    private void send(String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(properties.recipients().toArray(String[]::new));
            message.setFrom(properties.from());
            message.setSubject(properties.subjectPrefix() + " " + subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Sent email: {}", subject);
        } catch (RuntimeException exception) {
            log.error("Could not send email: {}", subject, exception);
        }
    }

    private String timestamp() {
        return TIMESTAMP.format(ZonedDateTime.now());
    }

    private record StatusSnapshot(boolean dockerAvailable, Map<String, String> containers, String dockerProblem) {
    }
}
