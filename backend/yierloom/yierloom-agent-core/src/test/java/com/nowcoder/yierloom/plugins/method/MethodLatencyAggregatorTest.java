package com.nowcoder.yierloom.plugins.method;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MethodLatencyAggregatorTest {

    @Test
    void createsStableSixteenHexDigitSignatureHash() {
        MethodKey key = MethodKey.from("com.example.Service", "work", "()V");

        assertThat(key.signatureHash()).isEqualTo("47f232ef0b4f7114");
        assertThat(key.signatureHash()).matches("[0-9a-f]{16}");
    }

    @Test
    void clampsNegativeDurationsAndUsesFixedP95BucketBounds() {
        MethodLatencyAggregator aggregator = new MethodLatencyAggregator(10);
        MethodKey key = MethodKey.from("example.Service", "work", "()V");

        aggregator.record(key, -10);
        IntStream.range(0, 95).forEach(ignored -> aggregator.record(key, 15));
        IntStream.range(0, 5).forEach(ignored -> aggregator.record(key, 2_000));

        MethodSnapshot snapshot = aggregator.topSnapshots(1).get(0);
        assertThat(snapshot.count()).isEqualTo(101);
        assertThat(snapshot.maxMs()).isEqualTo(2_000);
        assertThat(snapshot.p95Ms()).isEqualTo(20);
    }

    @Test
    void ordersByMaximumDurationThenSignatureHash() {
        MethodLatencyAggregator aggregator = new MethodLatencyAggregator(10);
        MethodKey fast = MethodKey.from("example.Fast", "call", "()V");
        MethodKey tiedA = MethodKey.from("example.TiedA", "call", "()V");
        MethodKey tiedB = MethodKey.from("example.TiedB", "call", "()V");
        aggregator.record(fast, 10);
        aggregator.record(tiedA, 100);
        aggregator.record(tiedB, 100);

        assertThat(aggregator.topSnapshots(3))
                .extracting(snapshot -> snapshot.key().signatureHash())
                .containsExactly(
                        tiedA.signatureHash().compareTo(tiedB.signatureHash()) < 0
                                ? tiedA.signatureHash()
                                : tiedB.signatureHash(),
                        tiedA.signatureHash().compareTo(tiedB.signatureHash()) < 0
                                ? tiedB.signatureHash()
                                : tiedA.signatureHash(),
                        fast.signatureHash());
    }

    @Test
    void keepsExistingKeysWhenCapacityRejectsNewObservations() {
        MethodLatencyAggregator aggregator = new MethodLatencyAggregator(1);
        MethodKey accepted = MethodKey.from("example.Accepted", "call", "()V");
        MethodKey rejected = MethodKey.from("example.Rejected", "call", "()V");

        assertThat(aggregator.record(accepted, 10)).isTrue();
        assertThat(aggregator.record(rejected, 20)).isFalse();
        assertThat(aggregator.record(rejected, 30)).isFalse();
        assertThat(aggregator.record(accepted, 40)).isTrue();

        assertThat(aggregator.droppedKeys()).isEqualTo(2);
        assertThat(aggregator.topSnapshots(1).get(0).count()).isEqualTo(2);
    }

    @Test
    void reportsOverflowBucketAsSixtySeconds() {
        MethodLatencyAggregator aggregator = new MethodLatencyAggregator(1);
        aggregator.record(MethodKey.from("example.Service", "work", "()V"), 90_000);

        assertThat(aggregator.topSnapshots(1).get(0).p95Ms()).isEqualTo(60_000);
    }
}
