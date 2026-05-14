package com.nowcoder.community.common.observability.autoconfig;

import com.nowcoder.community.common.observability.data.DataSourceRuntimeLogger;
import com.nowcoder.community.common.observability.executor.ExecutorRuntimeLogger;
import com.nowcoder.community.common.observability.jvm.JvmExtendedRuntimeLogger;
import com.nowcoder.community.common.observability.jvm.JvmRuntimeLogger;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import com.nowcoder.community.common.observability.system.ProcessResourceRuntimeLogger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RuntimeSnapshotSchedulerTest {

    @Test
    void periodicSnapshotChecksJvmExecutorsAndDatasource() {
        JvmRuntimeLogger jvmRuntimeLogger = mock(JvmRuntimeLogger.class);
        JvmExtendedRuntimeLogger jvmExtendedRuntimeLogger = mock(JvmExtendedRuntimeLogger.class);
        ExecutorRuntimeLogger executorRuntimeLogger = mock(ExecutorRuntimeLogger.class);
        DataSourceRuntimeLogger dataSourceRuntimeLogger = mock(DataSourceRuntimeLogger.class);
        ProcessResourceRuntimeLogger processResourceRuntimeLogger = mock(ProcessResourceRuntimeLogger.class);
        RuntimeSnapshotScheduler scheduler = new RuntimeSnapshotScheduler(
                jvmRuntimeLogger,
                fixedProvider(jvmExtendedRuntimeLogger),
                fixedProvider(executorRuntimeLogger),
                fixedProvider(dataSourceRuntimeLogger),
                fixedProvider(processResourceRuntimeLogger),
                new RuntimeLoggingProperties()
        );

        scheduler.logThresholdSnapshots();

        verify(jvmRuntimeLogger).logMemoryPressureSnapshot();
        verify(jvmExtendedRuntimeLogger).logExtendedSnapshots();
        verify(executorRuntimeLogger).logExecutorSnapshots();
        verify(dataSourceRuntimeLogger).logPoolSnapshot();
        verify(processResourceRuntimeLogger).logResourceSnapshots();
    }

    private static <T> ObjectProvider<T> fixedProvider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }
        };
    }
}
