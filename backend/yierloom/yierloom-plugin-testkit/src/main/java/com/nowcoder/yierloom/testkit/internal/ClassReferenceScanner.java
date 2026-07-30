package com.nowcoder.yierloom.testkit.internal;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.bytebuddy.jar.asm.AnnotationVisitor;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ConstantDynamic;
import net.bytebuddy.jar.asm.FieldVisitor;
import net.bytebuddy.jar.asm.Handle;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.ModuleVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.RecordComponentVisitor;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.jar.asm.TypePath;
import net.bytebuddy.jar.asm.signature.SignatureReader;
import net.bytebuddy.jar.asm.signature.SignatureVisitor;

/** Collects every class reference represented by a standard JVM class-file attribute. */
public final class ClassReferenceScanner {
    private static final Comparator<Reference> REFERENCE_ORDER = Comparator
            .comparing(Reference::binaryName)
            .thenComparing(reference -> reference.kind().name());

    private ClassReferenceScanner() {
    }

    public static ScanResult scan(byte[] classFile) {
        Objects.requireNonNull(classFile, "classFile");
        ReferenceCollector references = new ReferenceCollector();
        new ClassReader(classFile).accept(new ReferenceClassVisitor(references), 0);
        return references.result();
    }

    /**
     * Distinguishes executable owner and constant references from ordinary type references. This
     * lets Advice validation reject a reference to the Advice class without treating the method's
     * own declaration as a dependency.
     */
    public enum ReferenceKind {
        TYPE,
        FIELD_OWNER,
        METHOD_OWNER,
        HANDLE_OWNER,
        CLASS_LITERAL
    }

    public record Reference(String binaryName, ReferenceKind kind) {
        public Reference {
            if (binaryName == null || binaryName.isBlank()) {
                throw new IllegalArgumentException("binaryName must not be blank");
            }
            Objects.requireNonNull(kind, "kind");
        }
    }

    public record MethodKey(String name, String descriptor) implements Comparable<MethodKey> {
        public MethodKey {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(descriptor, "descriptor");
        }

        @Override
        public int compareTo(MethodKey other) {
            int byName = name.compareTo(other.name);
            return byName != 0 ? byName : descriptor.compareTo(other.descriptor);
        }
    }

    public record ScanResult(
            String className,
            Set<Reference> references,
            Map<MethodKey, Set<Reference>> methodBodyReferences
    ) {
        public ScanResult {
            Objects.requireNonNull(className, "className");
            references = immutableReferences(references);
            Objects.requireNonNull(methodBodyReferences, "methodBodyReferences");
            Map<MethodKey, Set<Reference>> methods = new TreeMap<>();
            methodBodyReferences.forEach((method, values) ->
                    methods.put(Objects.requireNonNull(method, "method"), immutableReferences(values)));
            methodBodyReferences = Collections.unmodifiableMap(new LinkedHashMap<>(methods));
        }

        public Set<String> referencedClassNames() {
            return binaryNames(references);
        }

        public Set<Reference> methodBodyReferences(String name, String descriptor) {
            return methodBodyReferences.getOrDefault(new MethodKey(name, descriptor), Set.of());
        }

        public Set<String> methodBodyClassNames(String name, String descriptor) {
            return binaryNames(methodBodyReferences(name, descriptor));
        }

        private static Set<Reference> immutableReferences(Set<Reference> values) {
            Objects.requireNonNull(values, "references");
            TreeSet<Reference> sorted = new TreeSet<>(REFERENCE_ORDER);
            sorted.addAll(values);
            return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
        }

        private static Set<String> binaryNames(Set<Reference> values) {
            TreeSet<String> names = new TreeSet<>();
            values.forEach(reference -> names.add(reference.binaryName()));
            return Collections.unmodifiableSet(new LinkedHashSet<>(names));
        }
    }

    private static final class ReferenceCollector {
        private final Set<Reference> references = new TreeSet<>(REFERENCE_ORDER);
        private final Map<MethodKey, Set<Reference>> methodReferences = new TreeMap<>();
        private String className;

        private ScanResult result() {
            if (className == null) {
                throw new IllegalArgumentException("class file has no class name");
            }
            return new ScanResult(className, references, methodReferences);
        }

        private void className(String internalName) {
            className = toBinaryName(internalName);
        }

        private void registerMethod(MethodKey method) {
            methodReferences.computeIfAbsent(method, ignored -> new TreeSet<>(REFERENCE_ORDER));
        }

        private void addInternalName(
                String internalName,
                ReferenceKind kind,
                MethodKey method
        ) {
            if (internalName == null) {
                return;
            }
            if (internalName.isEmpty()) {
                throw new IllegalArgumentException("empty internal class name");
            }
            char first = internalName.charAt(0);
            if (first == '[' || first == '(' || (first == 'L' && internalName.endsWith(";"))) {
                addDescriptor(internalName, kind, method);
                return;
            }
            add(toBinaryName(internalName), kind, method);
        }

        private void addDescriptor(String descriptor, ReferenceKind kind, MethodKey method) {
            if (descriptor != null) {
                addType(Type.getType(descriptor), kind, method);
            }
        }

        private void addType(Type type, ReferenceKind kind, MethodKey method) {
            switch (type.getSort()) {
                case Type.ARRAY -> addType(type.getElementType(), kind, method);
                case Type.OBJECT -> addInternalName(type.getInternalName(), kind, method);
                case Type.METHOD -> {
                    for (Type argument : type.getArgumentTypes()) {
                        addType(argument, kind, method);
                    }
                    addType(type.getReturnType(), kind, method);
                }
                default -> {
                    // Primitive and void descriptors do not reference a class.
                }
            }
        }

        private void addClassOrMethodSignature(String signature, MethodKey method) {
            if (signature != null) {
                new SignatureReader(signature).accept(new ReferenceSignatureVisitor(this, method));
            }
        }

        private void addTypeSignature(String signature, MethodKey method) {
            if (signature != null) {
                new SignatureReader(signature).acceptType(new ReferenceSignatureVisitor(this, method));
            }
        }

        private void addConstant(Object value, ReferenceKind typeKind, MethodKey method) {
            Set<Object> visiting = Collections.newSetFromMap(new IdentityHashMap<>());
            addConstant(value, typeKind, method, visiting);
        }

        private void addConstant(
                Object value,
                ReferenceKind typeKind,
                MethodKey method,
                Set<Object> visiting
        ) {
            if (value == null || isScalarConstant(value)) {
                return;
            }
            if (value instanceof Type type) {
                addType(type, typeKind, method);
                return;
            }
            if (value instanceof Handle handle) {
                addHandle(handle, method, visiting);
                return;
            }
            if (value instanceof ConstantDynamic dynamic) {
                if (!visiting.add(dynamic)) {
                    return;
                }
                try {
                    addDescriptor(dynamic.getDescriptor(), ReferenceKind.TYPE, method);
                    addHandle(dynamic.getBootstrapMethod(), method, visiting);
                    for (int index = 0; index < dynamic.getBootstrapMethodArgumentCount(); index++) {
                        addConstant(dynamic.getBootstrapMethodArgument(index), typeKind, method, visiting);
                    }
                } finally {
                    visiting.remove(dynamic);
                }
                return;
            }
            Class<?> valueType = value.getClass();
            if (valueType.isArray()) {
                if (valueType.getComponentType().isPrimitive() || !visiting.add(value)) {
                    return;
                }
                try {
                    for (int index = 0; index < Array.getLength(value); index++) {
                        addConstant(Array.get(value, index), typeKind, method, visiting);
                    }
                } finally {
                    visiting.remove(value);
                }
                return;
            }
            throw new IllegalArgumentException(
                    "unsupported class-file constant type: " + valueType.getName());
        }

        private void addHandle(Handle handle, MethodKey method, Set<Object> visiting) {
            if (!visiting.add(handle)) {
                return;
            }
            try {
                addInternalName(handle.getOwner(), ReferenceKind.HANDLE_OWNER, method);
                addDescriptor(handle.getDesc(), ReferenceKind.TYPE, method);
            } finally {
                visiting.remove(handle);
            }
        }

        private void addFrame(Object[] entries, int count, MethodKey method) {
            if (count == 0) {
                return;
            }
            if (entries == null || entries.length < count) {
                throw new IllegalArgumentException("malformed stack map frame");
            }
            for (int index = 0; index < count; index++) {
                Object entry = entries[index];
                if (entry instanceof String internalName) {
                    addInternalName(internalName, ReferenceKind.TYPE, method);
                } else if (entry instanceof Type type) {
                    addType(type, ReferenceKind.TYPE, method);
                } else if (entry != null && !(entry instanceof Integer) && !(entry instanceof Label)) {
                    throw new IllegalArgumentException(
                            "unsupported stack map frame entry: " + entry.getClass().getName());
                }
            }
        }

        private AnnotationVisitor annotation(String descriptor, MethodKey method) {
            addDescriptor(descriptor, ReferenceKind.TYPE, method);
            return new ReferenceAnnotationVisitor(this, method);
        }

        private void add(String binaryName, ReferenceKind kind, MethodKey method) {
            Reference reference = new Reference(binaryName, kind);
            references.add(reference);
            if (method != null) {
                methodReferences.computeIfAbsent(method, ignored -> new TreeSet<>(REFERENCE_ORDER))
                        .add(reference);
            }
        }

        private static boolean isScalarConstant(Object value) {
            return value instanceof Boolean || value instanceof Byte || value instanceof Character
                    || value instanceof Short || value instanceof Integer || value instanceof Long
                    || value instanceof Float || value instanceof Double || value instanceof String;
        }

        private static String toBinaryName(String internalName) {
            return internalName.replace('/', '.');
        }
    }

    private static final class ReferenceClassVisitor extends ClassVisitor {
        private final ReferenceCollector references;

        private ReferenceClassVisitor(ReferenceCollector references) {
            super(Opcodes.ASM9);
            this.references = references;
        }

        @Override
        public void visit(
                int version,
                int access,
                String name,
                String signature,
                String superName,
                String[] interfaces
        ) {
            references.className(name);
            references.addClassOrMethodSignature(signature, null);
            references.addInternalName(superName, ReferenceKind.TYPE, null);
            if (interfaces != null) {
                for (String interfaceName : interfaces) {
                    references.addInternalName(interfaceName, ReferenceKind.TYPE, null);
                }
            }
        }

        @Override
        public ModuleVisitor visitModule(String name, int access, String version) {
            return new ReferenceModuleVisitor(references);
        }

        @Override
        public void visitNestHost(String nestHost) {
            references.addInternalName(nestHost, ReferenceKind.TYPE, null);
        }

        @Override
        public void visitOuterClass(String owner, String name, String descriptor) {
            references.addInternalName(owner, ReferenceKind.TYPE, null);
            references.addDescriptor(descriptor, ReferenceKind.TYPE, null);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return references.annotation(descriptor, null);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(
                int typeRef,
                TypePath typePath,
                String descriptor,
                boolean visible
        ) {
            return references.annotation(descriptor, null);
        }

        @Override
        public void visitNestMember(String nestMember) {
            references.addInternalName(nestMember, ReferenceKind.TYPE, null);
        }

        @Override
        public void visitPermittedSubclass(String permittedSubclass) {
            references.addInternalName(permittedSubclass, ReferenceKind.TYPE, null);
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            references.addInternalName(name, ReferenceKind.TYPE, null);
            references.addInternalName(outerName, ReferenceKind.TYPE, null);
        }

        @Override
        public RecordComponentVisitor visitRecordComponent(
                String name,
                String descriptor,
                String signature
        ) {
            references.addDescriptor(descriptor, ReferenceKind.TYPE, null);
            references.addTypeSignature(signature, null);
            return new ReferenceRecordComponentVisitor(references);
        }

        @Override
        public FieldVisitor visitField(
                int access,
                String name,
                String descriptor,
                String signature,
                Object value
        ) {
            references.addDescriptor(descriptor, ReferenceKind.TYPE, null);
            references.addTypeSignature(signature, null);
            references.addConstant(value, ReferenceKind.TYPE, null);
            return new ReferenceFieldVisitor(references);
        }

        @Override
        public MethodVisitor visitMethod(
                int access,
                String name,
                String descriptor,
                String signature,
                String[] exceptions
        ) {
            MethodKey method = new MethodKey(name, descriptor);
            references.registerMethod(method);
            references.addDescriptor(descriptor, ReferenceKind.TYPE, null);
            references.addClassOrMethodSignature(signature, null);
            if (exceptions != null) {
                for (String exception : exceptions) {
                    references.addInternalName(exception, ReferenceKind.TYPE, null);
                }
            }
            return new ReferenceMethodVisitor(references, method);
        }
    }

    private static final class ReferenceModuleVisitor extends ModuleVisitor {
        private final ReferenceCollector references;

        private ReferenceModuleVisitor(ReferenceCollector references) {
            super(Opcodes.ASM9);
            this.references = references;
        }

        @Override
        public void visitMainClass(String mainClass) {
            references.addInternalName(mainClass, ReferenceKind.TYPE, null);
        }

        @Override
        public void visitUse(String service) {
            references.addInternalName(service, ReferenceKind.TYPE, null);
        }

        @Override
        public void visitProvide(String service, String... providers) {
            references.addInternalName(service, ReferenceKind.TYPE, null);
            if (providers != null) {
                for (String provider : providers) {
                    references.addInternalName(provider, ReferenceKind.TYPE, null);
                }
            }
        }
    }

    private static final class ReferenceRecordComponentVisitor extends RecordComponentVisitor {
        private final ReferenceCollector references;

        private ReferenceRecordComponentVisitor(ReferenceCollector references) {
            super(Opcodes.ASM9);
            this.references = references;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return references.annotation(descriptor, null);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(
                int typeRef,
                TypePath typePath,
                String descriptor,
                boolean visible
        ) {
            return references.annotation(descriptor, null);
        }
    }

    private static final class ReferenceFieldVisitor extends FieldVisitor {
        private final ReferenceCollector references;

        private ReferenceFieldVisitor(ReferenceCollector references) {
            super(Opcodes.ASM9);
            this.references = references;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return references.annotation(descriptor, null);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(
                int typeRef,
                TypePath typePath,
                String descriptor,
                boolean visible
        ) {
            return references.annotation(descriptor, null);
        }
    }

    private static final class ReferenceMethodVisitor extends MethodVisitor {
        private final ReferenceCollector references;
        private final MethodKey method;

        private ReferenceMethodVisitor(ReferenceCollector references, MethodKey method) {
            super(Opcodes.ASM9);
            this.references = references;
            this.method = method;
        }

        @Override
        public AnnotationVisitor visitAnnotationDefault() {
            return references.annotation(null, null);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return references.annotation(descriptor, null);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(
                int typeRef,
                TypePath typePath,
                String descriptor,
                boolean visible
        ) {
            return references.annotation(descriptor, null);
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(
                int parameter,
                String descriptor,
                boolean visible
        ) {
            return references.annotation(descriptor, null);
        }

        @Override
        public void visitFrame(
                int type,
                int numLocal,
                Object[] local,
                int numStack,
                Object[] stack
        ) {
            references.addFrame(local, numLocal, method);
            references.addFrame(stack, numStack, method);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            references.addInternalName(type, ReferenceKind.TYPE, method);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            references.addInternalName(owner, ReferenceKind.FIELD_OWNER, method);
            references.addDescriptor(descriptor, ReferenceKind.TYPE, method);
        }

        @Override
        public void visitMethodInsn(
                int opcode,
                String owner,
                String name,
                String descriptor,
                boolean isInterface
        ) {
            references.addInternalName(owner, ReferenceKind.METHOD_OWNER, method);
            references.addDescriptor(descriptor, ReferenceKind.TYPE, method);
        }

        @Override
        public void visitInvokeDynamicInsn(
                String name,
                String descriptor,
                Handle bootstrapMethodHandle,
                Object... bootstrapMethodArguments
        ) {
            references.addDescriptor(descriptor, ReferenceKind.TYPE, method);
            references.addConstant(bootstrapMethodHandle, ReferenceKind.CLASS_LITERAL, method);
            if (bootstrapMethodArguments != null) {
                for (Object argument : bootstrapMethodArguments) {
                    references.addConstant(argument, ReferenceKind.CLASS_LITERAL, method);
                }
            }
        }

        @Override
        public void visitLdcInsn(Object value) {
            references.addConstant(value, ReferenceKind.CLASS_LITERAL, method);
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            references.addDescriptor(descriptor, ReferenceKind.TYPE, method);
        }

        @Override
        public AnnotationVisitor visitInsnAnnotation(
                int typeRef,
                TypePath typePath,
                String descriptor,
                boolean visible
        ) {
            return references.annotation(descriptor, method);
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            references.addInternalName(type, ReferenceKind.TYPE, method);
        }

        @Override
        public AnnotationVisitor visitTryCatchAnnotation(
                int typeRef,
                TypePath typePath,
                String descriptor,
                boolean visible
        ) {
            return references.annotation(descriptor, method);
        }

        @Override
        public void visitLocalVariable(
                String name,
                String descriptor,
                String signature,
                Label start,
                Label end,
                int index
        ) {
            references.addDescriptor(descriptor, ReferenceKind.TYPE, method);
            references.addTypeSignature(signature, method);
        }

        @Override
        public AnnotationVisitor visitLocalVariableAnnotation(
                int typeRef,
                TypePath typePath,
                Label[] start,
                Label[] end,
                int[] index,
                String descriptor,
                boolean visible
        ) {
            return references.annotation(descriptor, method);
        }
    }

    private static final class ReferenceAnnotationVisitor extends AnnotationVisitor {
        private final ReferenceCollector references;
        private final MethodKey method;

        private ReferenceAnnotationVisitor(ReferenceCollector references, MethodKey method) {
            super(Opcodes.ASM9);
            this.references = references;
            this.method = method;
        }

        @Override
        public void visit(String name, Object value) {
            references.addConstant(value, ReferenceKind.TYPE, method);
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            references.addDescriptor(descriptor, ReferenceKind.TYPE, method);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String descriptor) {
            return references.annotation(descriptor, method);
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            return new ReferenceAnnotationVisitor(references, method);
        }
    }

    private static final class ReferenceSignatureVisitor extends SignatureVisitor {
        private final ReferenceCollector references;
        private final MethodKey method;
        private final List<String> classNames = new ArrayList<>();

        private ReferenceSignatureVisitor(ReferenceCollector references, MethodKey method) {
            super(Opcodes.ASM9);
            this.references = references;
            this.method = method;
        }

        @Override
        public void visitClassType(String name) {
            classNames.add(name);
            references.addInternalName(name, ReferenceKind.TYPE, method);
        }

        @Override
        public void visitInnerClassType(String name) {
            if (classNames.isEmpty()) {
                throw new IllegalArgumentException("inner class signature without outer class");
            }
            String outerName = classNames.remove(classNames.size() - 1);
            String className = outerName + '$' + name;
            classNames.add(className);
            references.addInternalName(className, ReferenceKind.TYPE, method);
        }

        @Override
        public void visitEnd() {
            if (classNames.isEmpty()) {
                throw new IllegalArgumentException("class signature ended without a class");
            }
            classNames.remove(classNames.size() - 1);
        }
    }
}
