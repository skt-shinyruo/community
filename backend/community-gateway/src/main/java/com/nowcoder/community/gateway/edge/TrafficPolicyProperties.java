package com.nowcoder.community.gateway.edge;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "gateway.http.traffic-policy")
public class TrafficPolicyProperties {

    private String defaultPolicyId = "default";
    private final Map<String, String> defaultTags = new LinkedHashMap<>();
    private final List<Rule> rules = new ArrayList<>();

    public String getDefaultPolicyId() {
        return defaultPolicyId;
    }

    public void setDefaultPolicyId(String defaultPolicyId) {
        this.defaultPolicyId = defaultPolicyId;
    }

    public Map<String, String> getDefaultTags() {
        return defaultTags;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public static class Rule {

        private boolean enabled = true;
        private String policyId;
        private final List<String> pathPrefixes = new ArrayList<>();
        private final List<String> methods = new ArrayList<>();
        private final Map<String, String> tags = new LinkedHashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPolicyId() {
            return policyId;
        }

        public void setPolicyId(String policyId) {
            this.policyId = policyId;
        }

        public List<String> getPathPrefixes() {
            return pathPrefixes;
        }

        public List<String> getMethods() {
            return methods;
        }

        public Map<String, String> getTags() {
            return tags;
        }
    }
}
