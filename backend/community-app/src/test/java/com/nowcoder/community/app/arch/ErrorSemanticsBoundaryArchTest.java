package com.nowcoder.community.app.arch;

import com.nowcoder.community.common.exception.ErrorCode;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(
        packages = "com.nowcoder.community.social.application",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ErrorSemanticsBoundaryArchTest {

    @ArchTest
    static final ArchRule social_not_found_classification_must_not_read_http_status =
            classes()
                    .that().haveSimpleName("LikeApplicationService")
                    .or().haveSimpleName("LikeCleanupReconciliationApplicationService")
                    .should(notCallErrorCodeMethod("getHttpStatus"))
                    .because("HTTP status mapping belongs to Servlet and WebFlux adapters");

    private static ArchCondition<JavaClass> notCallErrorCodeMethod(String methodName) {
        return new ArchCondition<>("not call ErrorCode." + methodName + "()") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                item.getMethodCallsFromSelf().stream()
                        .filter(call -> call.getTargetOwner().getName().equals(ErrorCode.class.getName()))
                        .filter(call -> call.getName().equals(methodName))
                        .forEach(call -> events.add(SimpleConditionEvent.violated(
                                item,
                                item.getName() + " calls transport method " + call.getDescription()
                        )));
            }
        };
    }
}
