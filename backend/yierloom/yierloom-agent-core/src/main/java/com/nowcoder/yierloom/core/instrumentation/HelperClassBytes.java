package com.nowcoder.yierloom.core.instrumentation;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.bytebuddy.jar.asm.ClassReader;

final class HelperClassBytes {
    private HelperClassBytes() {
    }

    static Map<String, byte[]> read(ClassLoader owner, Set<String> names) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(names, "names");
        List<String> requested = names.stream()
                .map(name -> Objects.requireNonNull(name, "Helper class name"))
                .sorted()
                .toList();
        Map<String, byte[]> bytes = new HashMap<>();
        Map<String, List<String>> dependencies = new HashMap<>();
        Set<String> requestedNames = Set.copyOf(requested);

        for (String name : requested) {
            byte[] value = readOne(owner, name);
            ClassReader reader;
            try {
                reader = new ClassReader(value);
            } catch (RuntimeException failure) {
                throw new IllegalArgumentException("invalid Helper class: " + name, failure);
            }
            String actualName = reader.getClassName().replace('/', '.');
            if (!name.equals(actualName)) {
                throw new IllegalArgumentException("Helper resource name mismatch: " + name);
            }
            bytes.put(name, value);
            dependencies.put(name, declaredDependencies(reader, requestedNames));
        }

        List<String> orderedNames = new ArrayList<>(requested.size());
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String name : requested) {
            order(name, dependencies, visiting, visited, orderedNames);
        }
        Map<String, byte[]> ordered = new LinkedHashMap<>();
        for (String name : orderedNames) {
            ordered.put(name, bytes.get(name));
        }
        return Collections.unmodifiableMap(ordered);
    }

    private static byte[] readOne(ClassLoader owner, String name) {
        String resource = name.replace('.', '/') + ".class";
        try (InputStream input = owner.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException("missing Helper class: " + name);
            }
            return input.readAllBytes();
        } catch (IOException failure) {
            throw new IllegalArgumentException("cannot read Helper class: " + name, failure);
        }
    }

    private static List<String> declaredDependencies(ClassReader reader, Set<String> requested) {
        List<String> dependencies = new ArrayList<>();
        addIfRequested(dependencies, reader.getSuperName(), requested);
        for (String interfaceName : reader.getInterfaces()) {
            addIfRequested(dependencies, interfaceName, requested);
        }
        return List.copyOf(dependencies);
    }

    private static void addIfRequested(
            List<String> dependencies,
            String internalName,
            Set<String> requested
    ) {
        if (internalName == null) {
            return;
        }
        String name = internalName.replace('/', '.');
        if (requested.contains(name)) {
            dependencies.add(name);
        }
    }

    private static void order(
            String name,
            Map<String, List<String>> dependencies,
            Set<String> visiting,
            Set<String> visited,
            List<String> result
    ) {
        if (visited.contains(name)) {
            return;
        }
        if (!visiting.add(name)) {
            throw new IllegalArgumentException("cyclic Helper dependency: " + name);
        }
        for (String dependency : dependencies.getOrDefault(name, List.of())) {
            order(dependency, dependencies, visiting, visited, result);
        }
        visiting.remove(name);
        visited.add(name);
        result.add(name);
    }
}
