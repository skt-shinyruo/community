package com.nowcoder.yierloom.api;

import java.util.concurrent.atomic.AtomicReference;

public final class YierLoomBridge {
    private static final AtomicReference<Endpoint> ENDPOINT = new AtomicReference<>();

    private YierLoomBridge() {
    }

    public interface Endpoint {
        boolean observe(String pluginId, PluginObservation observation);

        boolean emit(String pluginId, DiagnosticEvent event);
    }

    public static boolean install(Endpoint endpoint) {
        return endpoint != null && ENDPOINT.compareAndSet(null, endpoint);
    }

    public static boolean clear(Endpoint endpoint) {
        return endpoint != null && ENDPOINT.compareAndSet(endpoint, null);
    }

    public static boolean observe(String pluginId, PluginObservation observation) {
        return dispatch(pluginId, observation, endpoint -> endpoint.observe(pluginId, observation));
    }

    public static boolean emit(String pluginId, DiagnosticEvent event) {
        return dispatch(pluginId, event, endpoint -> endpoint.emit(pluginId, event));
    }

    static void clearForTests() {
        ENDPOINT.set(null);
    }

    private static boolean dispatch(String pluginId, Object message, EndpointDispatch dispatch) {
        if (pluginId == null || pluginId.isBlank() || message == null) {
            return false;
        }
        Endpoint endpoint = ENDPOINT.get();
        if (endpoint == null) {
            return false;
        }
        try {
            return dispatch.dispatch(endpoint);
        } catch (VirtualMachineError | ThreadDeath error) {
            throw error;
        } catch (Throwable error) {
            return false;
        }
    }

    @FunctionalInterface
    private interface EndpointDispatch {
        boolean dispatch(Endpoint endpoint);
    }
}
