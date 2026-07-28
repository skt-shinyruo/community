package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SynchronousCollaborationArchTest {

    private static final Pattern OWNED_PACKAGE = Pattern.compile(
            "^com\\.nowcoder\\.community\\.([^.]+)\\.(.+)$"
    );

    @Test
    void applicationCrossDomainDependenciesMustTargetPublishedSynchronousApis() {
        Set<String> violations = new TreeSet<>();
        for (JavaClass origin : new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.nowcoder.community")) {
            String originDomain = domain(origin);
            if (!ArchitectureRulesSupport.BUSINESS_OR_ADAPTER_DOMAINS.contains(originDomain)
                    || !isApplication(origin)) {
                continue;
            }
            for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                String targetDomain = domain(target);
                if (!ArchitectureRulesSupport.BUSINESS_OR_ADAPTER_DOMAINS.contains(targetDomain)
                        || originDomain.equals(targetDomain)
                        || isPublishedApi(target)) {
                    continue;
                }
                violations.add(origin.getFullName() + " -> " + target.getFullName());
            }
        }

        assertThat(violations)
                .as("cross-domain application dependencies outside api.query/api.action/api.model")
                .isEmpty();
    }

    @Test
    void coreApplicationSynchronousCollaborationGraphMustBeAcyclic() {
        Map<String, Set<String>> graph = new TreeMap<>();
        for (JavaClass origin : new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.nowcoder.community")) {
            String originDomain = domain(origin);
            if (!ArchitectureRulesSupport.CORE_DOMAINS.contains(originDomain) || !isApplication(origin)) {
                continue;
            }
            graph.computeIfAbsent(originDomain, ignored -> new TreeSet<>());
            for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                String targetDomain = domain(target);
                if (!ArchitectureRulesSupport.CORE_DOMAINS.contains(targetDomain)
                        || originDomain.equals(targetDomain)
                        || !isPublishedApi(target)) {
                    continue;
                }
                graph.computeIfAbsent(targetDomain, ignored -> new TreeSet<>());
                graph.get(originDomain).add(targetDomain);
            }
        }

        assertThat(findCycles(graph))
                .as("core synchronous owner-domain graph must not contain strongly connected cycles")
                .isEmpty();
    }

    private static List<String> findCycles(Map<String, Set<String>> graph) {
        Map<String, VisitState> states = new TreeMap<>();
        graph.keySet().forEach(node -> states.put(node, VisitState.UNVISITED));
        List<String> cycles = new ArrayList<>();
        Deque<String> path = new ArrayDeque<>();
        for (String node : graph.keySet()) {
            if (states.get(node) == VisitState.UNVISITED) {
                findCyclesFrom(node, graph, states, path, cycles);
            }
        }
        return cycles;
    }

    private static void findCyclesFrom(
            String node,
            Map<String, Set<String>> graph,
            Map<String, VisitState> states,
            Deque<String> path,
            List<String> cycles
    ) {
        states.put(node, VisitState.VISITING);
        path.addLast(node);
        for (String target : graph.getOrDefault(node, Set.of())) {
            VisitState targetState = states.getOrDefault(target, VisitState.UNVISITED);
            if (targetState == VisitState.UNVISITED) {
                findCyclesFrom(target, graph, states, path, cycles);
                continue;
            }
            if (targetState == VisitState.VISITING) {
                List<String> currentPath = new ArrayList<>(path);
                int cycleStart = currentPath.indexOf(target);
                List<String> cycle = new ArrayList<>(currentPath.subList(cycleStart, currentPath.size()));
                cycle.add(target);
                cycles.add(String.join(" -> ", cycle));
            }
        }
        path.removeLast();
        states.put(node, VisitState.VISITED);
    }

    private static boolean isApplication(JavaClass type) {
        Matcher matcher = OWNED_PACKAGE.matcher(type.getPackageName());
        return matcher.matches()
                && (matcher.group(2).equals("application") || matcher.group(2).startsWith("application."));
    }

    private static boolean isPublishedApi(JavaClass type) {
        Matcher matcher = OWNED_PACKAGE.matcher(type.getPackageName());
        if (!matcher.matches()) {
            return false;
        }
        String ownedPackage = matcher.group(2);
        return ownedPackage.equals("api.query")
                || ownedPackage.startsWith("api.query.")
                || ownedPackage.equals("api.action")
                || ownedPackage.startsWith("api.action.")
                || ownedPackage.equals("api.model")
                || ownedPackage.startsWith("api.model.");
    }

    private static String domain(JavaClass type) {
        Matcher matcher = OWNED_PACKAGE.matcher(type.getPackageName());
        return matcher.matches() ? matcher.group(1) : "";
    }

    private enum VisitState {
        UNVISITED,
        VISITING,
        VISITED
    }
}
