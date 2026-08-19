final class MetricTagScannerFixture {
  void record(Object registry, Object builder) {
    Tag.of("result", "value.only.tag");
    Tags.of("cache", "value.only.tags", "userId", "value.only.tags.forbidden");
    builder.tag("objectKey", "value.only.builder.tag");
    builder.tags("scope", "value.only.builder.tags", "orderId", "value.only.builder.tags.forbidden");
    registry.counter("value.only.counter.metric", "result", "value.only.counter", "redisKey", "value.only.counter.forbidden");
    registry.timer("value.only.timer.metric", "job.name", "value.only.timer", "url.full", "value.only.timer.forbidden");
    registry.summary("value.only.summary.metric", "event.type", "value.only.summary", "client.ip", "value.only.summary.forbidden");
    registry.gauge("value.only.gauge.metric", "pool.name", "value.only.gauge", "trace.id", "value.only.gauge.forbidden");
  }
}
