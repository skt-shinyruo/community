package com.example.runtimediagnostics.integration;

public class AgentTargetMain {

    public static void main(String[] args) {
        AgentTargetService service = new AgentTargetService();
        service.slowWork();
        try {
            service.throwingWork();
        } catch (IllegalStateException expected) {
            System.err.println("target exception propagated");
        }
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
