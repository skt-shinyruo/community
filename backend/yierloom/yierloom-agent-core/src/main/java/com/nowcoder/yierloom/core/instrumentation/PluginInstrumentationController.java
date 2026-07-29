package com.nowcoder.yierloom.core.instrumentation;

import com.nowcoder.yierloom.core.plugin.ValidatedPlugin;

public interface PluginInstrumentationController {
    void install(ValidatedPlugin plugin) throws Exception;

    void removePlugin(String pluginId);

    void removeAll();
}
