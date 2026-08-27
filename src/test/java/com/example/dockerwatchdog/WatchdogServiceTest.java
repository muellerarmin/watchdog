package com.example.dockerwatchdog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchdogServiceTest {
    @Test
    void alertsOnceForFailureAndAgainForRecovery() throws Exception {
        DockerClient dockerClient = mock(DockerClient.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        WatchdogProperties properties = new WatchdogProperties(List.of("api"), 60_000, "docker", List.of("ops@example.com"), "watchdog@example.com", "[Watchdog]");
        WatchdogService service = new WatchdogService(dockerClient, mailSender, properties);

        when(dockerClient.listContainers())
                .thenReturn(List.of(new DockerContainer("api", "running", "healthy")))
                .thenReturn(List.of(new DockerContainer("api", "exited", null)))
                .thenReturn(List.of(new DockerContainer("api", "exited", null)))
                .thenReturn(List.of(new DockerContainer("api", "running", "healthy")));

        service.checkStatus();
        service.checkStatus();
        service.checkStatus();
        service.checkStatus();

        ArgumentCaptor<SimpleMailMessage> messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, org.mockito.Mockito.times(2)).send(messages.capture());
        assertThat(messages.getAllValues().get(0).getFrom()).isEqualTo("watchdog@example.com");
        assertThat(messages.getAllValues().get(0).getText()).contains("api: offline (exited)");
        assertThat(messages.getAllValues().get(1).getText()).contains("api: recovered");
        verifyNoMoreInteractions(mailSender);
    }

    @Test
    void alertsOnceForRepeatedDockerCommandFailuresAndAgainForRecovery() throws Exception {
        DockerClient dockerClient = mock(DockerClient.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        WatchdogProperties properties = new WatchdogProperties(List.of("api"), 60_000, "docker", List.of("ops@example.com"), "watchdog@example.com", "[Watchdog]");
        WatchdogService service = new WatchdogService(dockerClient, mailSender, properties);

        when(dockerClient.listContainers())
                .thenThrow(new IOException("Failed to exec spawn helper: pid: 1"))
                .thenThrow(new IOException("Failed to exec spawn helper: pid: 2"))
                .thenReturn(List.of(new DockerContainer("api", "running", "healthy")));

        service.checkStatus();
        service.checkStatus();
        service.checkStatus();

        ArgumentCaptor<SimpleMailMessage> messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, org.mockito.Mockito.times(2)).send(messages.capture());
        assertThat(messages.getAllValues().get(0).getText()).contains("Docker daemon: unavailable");
        assertThat(messages.getAllValues().get(1).getText()).contains("Docker daemon: recovered");
        verifyNoMoreInteractions(mailSender);
    }
}
