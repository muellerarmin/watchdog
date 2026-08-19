package com.example.dockerwatchdog;

public record DockerContainer(String name, String status, String health) {
    public String problem() {
        if (!"running".equals(status)) {
            return "offline (" + status + ")";
        }
        if (health != null && !"healthy".equals(health)) {
            return "unhealthy (" + health + ")";
        }
        return null;
    }
}
