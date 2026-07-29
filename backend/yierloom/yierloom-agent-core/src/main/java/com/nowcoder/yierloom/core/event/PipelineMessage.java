package com.nowcoder.yierloom.core.event;

import com.nowcoder.yierloom.api.DiagnosticEvent;
import com.nowcoder.yierloom.api.PluginObservation;

sealed interface PipelineMessage permits PipelineMessage.Observation, PipelineMessage.Event {
    String pluginId();

    record Observation(String pluginId, PluginObservation value) implements PipelineMessage {
    }

    record Event(String pluginId, DiagnosticEvent value) implements PipelineMessage {
    }
}
