package com.nowcoder.yierloom.api;

public interface RuntimeCapability {
    void start(PluginRuntimeContext context) throws Exception;

    void stop() throws Exception;
}
