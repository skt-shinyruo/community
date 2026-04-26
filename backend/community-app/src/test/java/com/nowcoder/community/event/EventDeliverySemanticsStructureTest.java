package com.nowcoder.community.event;

import com.nowcoder.community.common.event.BestEffortLocalEventListener;
import com.nowcoder.community.content.event.SocialInteractionProjectionListener;
import com.nowcoder.community.notice.event.NoticeProjectionListener;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EventDeliverySemanticsStructureTest {

    @Test
    void afterCommitLocalListenersShouldDeclareBestEffortSemantics() throws Exception {
        Set<Class<?>> listeners = afterCommitLocalListeners();

        assertThat(listeners)
                .extracting(Class::getName)
                .contains(
                        NoticeProjectionListener.class.getName(),
                        SocialInteractionProjectionListener.class.getName()
                );

        for (Class<?> listener : listeners) {
            BestEffortLocalEventListener annotation = listener.getAnnotation(BestEffortLocalEventListener.class);
            assertThat(annotation)
                    .as(listener.getName())
                    .isNotNull();
            assertThat(annotation.reason())
                    .as(listener.getName())
                    .isNotBlank();
        }
    }

    private Set<Class<?>> afterCommitLocalListeners() throws Exception {
        Set<Class<?>> listeners = new LinkedHashSet<>();
        for (Class<?> type : productionClasses()) {
            if (hasAfterCommitListener(type)) {
                listeners.add(type);
            }
        }
        return listeners;
    }

    private Set<Class<?>> productionClasses() throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> roots = classLoader.getResources("com/nowcoder/community");
        Set<String> classNames = new LinkedHashSet<>();
        while (roots.hasMoreElements()) {
            URL url = roots.nextElement();
            if (!"file".equals(url.getProtocol())) {
                continue;
            }
            Path root = Path.of(url.toURI());
            if (root.toString().contains("target/test-classes")) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(path -> path.toString().endsWith(".class"))
                        .filter(path -> !path.getFileName().toString().contains("$"))
                        .map(root::relativize)
                        .map(EventDeliverySemanticsStructureTest::toClassName)
                        .forEach(classNames::add);
            }
        }

        Set<Class<?>> classes = new LinkedHashSet<>();
        for (String className : classNames) {
            classes.add(Class.forName(className, false, classLoader));
        }
        return classes;
    }

    private static String toClassName(Path relativeClassFile) {
        String relativeName = relativeClassFile.toString();
        String withoutSuffix = relativeName.substring(0, relativeName.length() - ".class".length());
        return "com.nowcoder.community." + withoutSuffix.replace('/', '.').replace('\\', '.');
    }

    private static boolean hasAfterCommitListener(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);
            if (annotation != null && annotation.phase() == TransactionPhase.AFTER_COMMIT) {
                return true;
            }
        }
        return false;
    }
}
