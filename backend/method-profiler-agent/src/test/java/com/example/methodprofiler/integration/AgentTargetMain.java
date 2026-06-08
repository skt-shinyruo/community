package com.example.methodprofiler.integration;

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
            Thread.sleep(1200);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
