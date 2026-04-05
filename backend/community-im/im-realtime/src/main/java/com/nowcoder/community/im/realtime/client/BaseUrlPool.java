package com.nowcoder.community.im.realtime.client;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class BaseUrlPool {

    private final String name;
    private final List<String> baseUrls;
    private final AtomicInteger cursor = new AtomicInteger(0);

    private BaseUrlPool(String name, List<String> baseUrls) {
        this.name = StringUtils.hasText(name) ? name.trim() : "upstream";
        this.baseUrls = List.copyOf(baseUrls);
    }

    public static BaseUrlPool from(String name, String baseUrl, List<String> baseUrls) {
        Set<String> normalized = new LinkedHashSet<>();
        if (StringUtils.hasText(baseUrl)) {
            normalized.add(baseUrl.trim());
        }
        if (baseUrls != null) {
            for (String candidate : baseUrls) {
                if (StringUtils.hasText(candidate)) {
                    normalized.add(candidate.trim());
                }
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must define at least one base URL");
        }
        return new BaseUrlPool(name, new ArrayList<>(normalized));
    }

    public static BaseUrlPool from(String name, List<String> baseUrls) {
        return from(name, null, baseUrls);
    }

    public List<String> nextCandidates() {
        int size = baseUrls.size();
        int start = Math.floorMod(cursor.getAndIncrement(), size);
        ArrayList<String> ordered = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ordered.add(baseUrls.get((start + i) % size));
        }
        return List.copyOf(ordered);
    }

    public List<String> baseUrls() {
        return baseUrls;
    }

    public String name() {
        return name;
    }
}
