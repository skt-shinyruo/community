package com.nowcoder.observability.methodprofiler.stats;

import com.nowcoder.observability.methodprofiler.model.MethodKey;
import com.nowcoder.observability.methodprofiler.model.MethodSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MethodLatencyAggregatorTest {

    @Test
    void summarizesTopMethodsByMaxDurationDescending() {
        MethodLatencyAggregator aggregator = new MethodLatencyAggregator(10);
        MethodKey first = new MethodKey("com.example.First", "run", "0000000000000001");
        MethodKey second = new MethodKey("com.example.Second", "run", "0000000000000002");

        aggregator.record(first, 10);
        aggregator.record(first, 30);
        aggregator.record(second, 100);

        List<MethodSnapshot> snapshots = aggregator.topSnapshots(2);

        assertThat(snapshots).hasSize(2);
        assertThat(snapshots.get(0).key()).isEqualTo(second);
        assertThat(snapshots.get(0).maxMs()).isEqualTo(100);
        assertThat(snapshots.get(1).key()).isEqualTo(first);
        assertThat(snapshots.get(1).count()).isEqualTo(2);
        assertThat(snapshots.get(1).avgMs()).isEqualTo(20);
    }

    @Test
    void capsTrackedMethodKeys() {
        MethodLatencyAggregator aggregator = new MethodLatencyAggregator(1);

        aggregator.record(new MethodKey("com.example.First", "run", "0000000000000001"), 10);
        aggregator.record(new MethodKey("com.example.Second", "run", "0000000000000002"), 20);

        assertThat(aggregator.topSnapshots(10)).hasSize(1);
        assertThat(aggregator.droppedMethodKeys()).isEqualTo(1);
    }
}
