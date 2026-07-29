package com.nowcoder.yierloom.sdk;

import java.util.List;

import com.nowcoder.yierloom.api.PluginConfig;

public interface InstrumentationCapability {
    List<InstrumentationModule> instrumentations(PluginConfig config);
}
