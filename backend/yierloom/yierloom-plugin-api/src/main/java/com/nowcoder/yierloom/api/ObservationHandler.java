package com.nowcoder.yierloom.api;

@FunctionalInterface
public interface ObservationHandler {
    void onObservation(PluginObservation observation) throws Exception;
}
