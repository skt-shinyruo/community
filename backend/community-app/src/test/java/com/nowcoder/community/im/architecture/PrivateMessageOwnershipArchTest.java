package com.nowcoder.community.im.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.nowcoder.community",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class PrivateMessageOwnershipArchTest {

    @ArchTest
    static final ArchRule message_runtime_must_not_keep_private_message_governance_action_api =
            noClasses()
                    .that().resideInAnyPackage("..message.api.action..")
                    .should().haveSimpleName("PrivateMessageGovernanceActionApi")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule message_runtime_must_not_keep_private_message_governance_service =
            noClasses()
                    .that().resideInAnyPackage("..message.service..")
                    .should().haveSimpleName("PrivateMessageGovernanceService")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule message_runtime_must_not_keep_private_message_user_moderation_guard =
            noClasses()
                    .that().resideInAnyPackage("..message.service..")
                    .should().haveSimpleName("UserModerationGuard")
                    .allowEmptyShould(true);
}
