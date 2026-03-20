package com.nowcoder.community.gateway.edge;

import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.LinkedHashMap;
import java.util.Map;

public class TrafficPolicyEvaluator {

    private final TrafficPolicyProperties properties;

    public TrafficPolicyEvaluator(TrafficPolicyProperties properties) {
        this.properties = properties;
    }

    public Decision evaluate(ServerHttpRequest request) {
        TrafficPolicyProperties.Rule bestRule = null;
        int bestPrefixLength = -1;
        String path = request == null ? "" : request.getPath().value();
        String method = request == null || request.getMethod() == null ? "" : request.getMethod().name();

        for (TrafficPolicyProperties.Rule rule : properties.getRules()) {
            if (rule == null || !rule.isEnabled()) {
                continue;
            }
            if (!methodMatches(rule, method)) {
                continue;
            }
            int prefixLength = matchingPrefixLength(rule, path);
            if (prefixLength < 0) {
                continue;
            }
            if (prefixLength > bestPrefixLength) {
                bestPrefixLength = prefixLength;
                bestRule = rule;
            }
        }

        LinkedHashMap<String, String> tags = new LinkedHashMap<>(properties.getDefaultTags());
        String policyId = properties.getDefaultPolicyId();
        if (bestRule != null) {
            tags.putAll(bestRule.getTags());
            if (bestRule.getPolicyId() != null && !bestRule.getPolicyId().isBlank()) {
                policyId = bestRule.getPolicyId();
            }
        }
        return new Decision(policyId, Map.copyOf(tags));
    }

    private static boolean methodMatches(TrafficPolicyProperties.Rule rule, String method) {
        if (rule.getMethods().isEmpty()) {
            return true;
        }
        for (String allowed : rule.getMethods()) {
            if (allowed != null && allowed.equalsIgnoreCase(method)) {
                return true;
            }
        }
        return false;
    }

    private static int matchingPrefixLength(TrafficPolicyProperties.Rule rule, String path) {
        int best = -1;
        for (String prefix : rule.getPathPrefixes()) {
            if (prefix != null && !prefix.isBlank() && path.startsWith(prefix)) {
                best = Math.max(best, prefix.length());
            }
        }
        return best;
    }

    public record Decision(String policyId, Map<String, String> tags) {
    }
}
