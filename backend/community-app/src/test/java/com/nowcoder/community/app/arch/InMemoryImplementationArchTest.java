package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.nowcoder.community",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class InMemoryImplementationArchTest {

    @ArchTest
    static final ArchRule production_code_must_not_define_in_memory_implementations =
            noClasses()
                    .should().haveSimpleNameStartingWith("InMemory")
                    .because("distributed runtime state must use durable shared infrastructure, not process-local memory");
}
