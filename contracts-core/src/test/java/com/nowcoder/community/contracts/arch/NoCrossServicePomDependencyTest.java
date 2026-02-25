package com.nowcoder.community.contracts.arch;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 架构门禁（Maven 依赖层面）：禁止 service 模块直接依赖其他 service 模块。
 *
 * <p>目的：强制跨服务协作通过 {@code *-api}（RPC 契约）或事件契约完成，避免形成“实现级耦合网”。</p>
 */
class NoCrossServicePomDependencyTest {

    private static final String PROJECT_GROUP_ID = "com.nowcoder.community";

    @Test
    void serviceModulesShouldNotDependOnOtherServiceModules() throws Exception {
        Path repoRoot = findRepoRoot(Paths.get("").toAbsolutePath());
        Path rootPom = repoRoot.resolve("pom.xml");

        Set<String> modules = parseModulesFromRootPom(rootPom);
        Set<String> serviceModules = new HashSet<>();
        for (String m : modules) {
            if (m == null || m.isBlank()) {
                continue;
            }
            if (m.endsWith("-service") || m.equals("gateway") || m.equals("ops-service")) {
                serviceModules.add(m);
            }
        }

        List<String> violations = new ArrayList<>();
        for (String module : serviceModules) {
            Path pom = repoRoot.resolve(module).resolve("pom.xml");
            if (!Files.exists(pom)) {
                continue;
            }
            List<String> deps = parseProjectDependencies(pom);
            for (String artifactId : deps) {
                if (serviceModules.contains(artifactId)) {
                    violations.add("[cross-service-dependency] module=" + module + " dependsOn=" + artifactId);
                }
            }
        }

        assertThat(violations)
                .as("Detected forbidden service->service pom dependencies:\n" + String.join("\n", violations))
                .isEmpty();
    }

    private Set<String> parseModulesFromRootPom(Path rootPom) throws Exception {
        Set<String> modules = new HashSet<>();
        var doc = newSecureDocumentBuilderFactory().newDocumentBuilder().parse(rootPom.toFile());
        var nodes = doc.getElementsByTagName("module");
        for (int i = 0; i < nodes.getLength(); i++) {
            var node = nodes.item(i);
            if (node == null || node.getTextContent() == null) {
                continue;
            }
            String text = node.getTextContent().trim();
            if (!text.isEmpty()) {
                modules.add(text);
            }
        }
        return modules;
    }

    private List<String> parseProjectDependencies(Path pom) throws Exception {
        List<String> artifactIds = new ArrayList<>();
        var doc = newSecureDocumentBuilderFactory().newDocumentBuilder().parse(pom.toFile());
        var dependencies = doc.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            var dep = dependencies.item(i);
            if (dep == null) {
                continue;
            }
            String groupId = textOfChild(dep, "groupId");
            if (!PROJECT_GROUP_ID.equals(groupId)) {
                continue;
            }
            String artifactId = textOfChild(dep, "artifactId");
            if (artifactId != null && !artifactId.isBlank()) {
                artifactIds.add(artifactId.trim());
            }
        }
        return artifactIds;
    }

    private DocumentBuilderFactory newSecureDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (Exception ignored) {
            // best-effort：在不支持某些 feature 的实现上也不阻断测试
        }
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private String textOfChild(org.w3c.dom.Node parent, String tagName) {
        if (parent == null || tagName == null) {
            return null;
        }
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            var c = children.item(i);
            if (c == null || c.getNodeName() == null) {
                continue;
            }
            if (!tagName.equals(c.getNodeName())) {
                continue;
            }
            return c.getTextContent() == null ? null : c.getTextContent().trim();
        }
        return null;
    }

    private Path findRepoRoot(Path start) {
        Path cur = start;
        while (cur != null) {
            if (Files.exists(cur.resolve("pom.xml"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        return start;
    }
}
