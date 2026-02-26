package com.nowcoder.community.analytics.repo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Repository
@ConditionalOnProperty(name = "analytics.storage", havingValue = "memory")
public class InMemoryAnalyticsRepository implements AnalyticsRepository {

    private final Map<LocalDate, Set<String>> uv = new HashMap<>();
    private final Map<LocalDate, BitSet> dau = new HashMap<>();

    @Override
    public synchronized void recordUv(LocalDate date, String ip) {
        if (!StringUtils.hasText(ip)) {
            return;
        }
        uv.computeIfAbsent(date, k -> new HashSet<>()).add(ip);
    }

    @Override
    public synchronized long calculateUv(LocalDate start, LocalDate end) {
        Set<String> all = new HashSet<>();
        LocalDate d = start;
        while (!d.isAfter(end)) {
            Set<String> s = uv.get(d);
            if (s != null) {
                all.addAll(s);
            }
            d = d.plusDays(1);
        }
        return all.size();
    }

    @Override
    public synchronized void recordDau(LocalDate date, int userId) {
        if (userId <= 0) {
            return;
        }
        dau.computeIfAbsent(date, k -> new BitSet()).set(userId);
    }

    @Override
    public synchronized long calculateDau(LocalDate start, LocalDate end) {
        BitSet all = new BitSet();
        LocalDate d = start;
        while (!d.isAfter(end)) {
            BitSet bs = dau.get(d);
            if (bs != null) {
                all.or(bs);
            }
            d = d.plusDays(1);
        }
        return all.cardinality();
    }
}

