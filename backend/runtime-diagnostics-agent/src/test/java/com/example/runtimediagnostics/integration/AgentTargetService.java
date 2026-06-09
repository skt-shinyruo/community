package com.example.runtimediagnostics.integration;

public class AgentTargetService {

    public String slowWork() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return "done";
    }

    public void throwingWork() {
        throw new IllegalStateException("target failure password=secret");
    }
}
