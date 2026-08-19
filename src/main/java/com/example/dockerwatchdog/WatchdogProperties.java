package com.example.dockerwatchdog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "watchdog")
public record WatchdogProperties(
        @NotEmpty List<String> containers,
        @Positive long checkIntervalMs,
        @NotBlank String dockerCommand,
        @NotEmpty List<String> recipients,
        @NotBlank String from,
        @NotBlank String subjectPrefix) {
}
