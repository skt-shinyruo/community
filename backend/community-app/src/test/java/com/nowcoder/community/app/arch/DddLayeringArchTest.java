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
class DddLayeringArchTest {

    @ArchTest
    static final ArchRule domain_must_not_depend_on_outer_layers =
            noClasses()
                    .that().resideInAnyPackage(
                            "..domain.model..",
                            "..domain.service..",
                            "..domain.repository..",
                            "..domain.event.."
                    )
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..controller..",
                            "..application..",
                            "..infrastructure..",
                            "..mapper..",
                            "..entity..",
                            "..dto..",
                            "..api.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule application_must_not_depend_on_transport_or_infrastructure =
            noClasses()
                    .that().resideInAnyPackage("..application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..controller..",
                            "..app..",
                            "..content.service..",
                            "..infrastructure..",
                            "..mapper..",
                            "..entity..",
                            "..dto.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule production_code_must_not_use_use_case_naming =
            noClasses()
                    .should().haveSimpleNameEndingWith("UseCase")
                    .because("ApplicationService is the use-case entry; do not add a parallel UseCase layer");

    @ArchTest
    static final ArchRule controllers_must_not_depend_on_domain_or_infrastructure =
            noClasses()
                    .that().resideInAnyPackage("..controller..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..domain..",
                            "..infrastructure..",
                            "..mapper..",
                            "..entity.."
                    );

    @ArchTest
    static final ArchRule legacy_business_root_packages_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage(
                            "..analytics.service..",
                            "..analytics.repo..",
                            "..analytics.ingest..",
                            "..auth.dto..",
                            "..auth.service..",
                            "..auth.web..",
                            "..content.app..",
                            "..content.assembler..",
                            "..content.dto..",
                            "..content.entity..",
                            "..content.event..",
                            "..content.like..",
                            "..content.mapper..",
                            "..content.score..",
                            "..content.service..",
                            "..content.text..",
                            "..content.util..",
                            "..content.domain.assembler..",
                            "..growth.entity..",
                            "..growth.event..",
                            "..growth.mapper..",
                            "..growth.service..",
                            "..im.service..",
                            "..market.entity..",
                            "..market.job..",
                            "..market.mapper..",
                            "..market.model..",
                            "..market.service..",
                            "..notice.entity..",
                            "..notice.event..",
                            "..notice.mapper..",
                            "..notice.service..",
                            "..ops.dto..",
                            "..search.event..",
                            "..search.repo..",
                            "..search.service..",
                            "..social.block..",
                            "..social.dto..",
                            "..social.event..",
                            "..social.follow..",
                            "..social.like..",
                            "..social.service..",
                            "..user.dto..",
                            "..user.entity..",
                            "..user.event..",
                            "..user.mapper..",
                            "..user.service..",
                            "..user.session..",
                            "..wallet.entity..",
                            "..wallet.mapper..",
                            "..wallet.model..",
                            "..wallet.service.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule user_controllers_must_not_depend_on_legacy_user_surfaces =
            noClasses()
                    .that().resideInAnyPackage("..user.controller..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..user.service..",
                            "..user.mapper..",
                            "..user.entity..",
                            "..user.infrastructure.."
                    );

    @ArchTest
    static final ArchRule auth_service_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..auth.service..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule legacy_auth_web_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..auth.web..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule auth_controller_and_adapters_must_not_depend_on_legacy_surfaces =
            noClasses()
                    .that().resideInAnyPackage(
                            "..auth.controller..",
                            "..auth.infrastructure.job..",
                            "..auth.infrastructure.web.."
                    )
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..auth.service..",
                            "..auth.web..",
                            "..auth.domain..",
                            "..auth.infrastructure.persistence..",
                            "..auth.infrastructure.jwt..",
                            "..auth.infrastructure.mail.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule ops_controller_must_call_ops_application_only =
            noClasses()
                    .that().resideInAnyPackage("..ops.controller..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..search.api..",
                            "..ops.infrastructure..",
                            "..ops.dto.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule legacy_ops_dto_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..ops.dto..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule user_service_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..user.service..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule legacy_user_mapper_and_entity_packages_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..user.mapper..", "..user.entity..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule social_service_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..social.service..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule legacy_social_feature_packages_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage(
                            "..social.like..",
                            "..social.follow..",
                            "..social.block..",
                            "..social.event.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule social_controllers_must_not_depend_on_legacy_social_surfaces =
            noClasses()
                    .that().resideInAnyPackage("..social.controller..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..social.service..",
                            "..social.infrastructure..",
                            "..social.domain..",
                            "..social.like..",
                            "..social.follow..",
                            "..social.block..",
                            "..social.event.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule wallet_service_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..wallet.service..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule legacy_wallet_mapper_entity_model_packages_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..wallet.mapper..", "..wallet.entity..", "..wallet.model..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule wallet_controllers_must_not_depend_on_legacy_wallet_surfaces =
            noClasses()
                    .that().resideInAnyPackage("..wallet.controller..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..wallet.service..",
                            "..wallet.infrastructure..",
                            "..wallet.domain..",
                            "..wallet.mapper..",
                            "..wallet.entity..",
                            "..wallet.model.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule market_service_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..market.service..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule legacy_market_mapper_entity_model_packages_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..market.mapper..", "..market.entity..", "..market.model..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule market_controllers_and_jobs_must_not_depend_on_legacy_market_surfaces =
            noClasses()
                    .that().resideInAnyPackage("..market.controller..", "..market.infrastructure.job..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..market.service..",
                            "..market.infrastructure..",
                            "..market.domain..",
                            "..market.mapper..",
                            "..market.entity..",
                            "..market.model.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule growth_service_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..growth.service..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule legacy_growth_mapper_entity_packages_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..growth.mapper..", "..growth.entity..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule notice_service_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..notice.service..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule legacy_notice_mapper_entity_event_packages_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..notice.mapper..", "..notice.entity..", "..notice.event..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule notice_controllers_and_listeners_must_not_depend_on_legacy_notice_surfaces =
            noClasses()
                    .that().resideInAnyPackage("..notice.controller..", "..notice.infrastructure.event..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..notice.service..",
                            "..notice.mapper..",
                            "..notice.entity..",
                            "..notice.event.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule search_service_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..search.service..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule legacy_search_repo_event_packages_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..search.repo..", "..search.event..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule search_controller_and_event_adapters_must_not_depend_on_legacy_surfaces =
            noClasses()
                    .that().resideInAnyPackage("..search.controller..", "..search.infrastructure.event..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..search.service..",
                            "..search.repo..",
                            "..search.event..",
                            "..search.infrastructure.persistence..",
                            "..search.domain.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule analytics_service_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..analytics.service..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule legacy_analytics_repo_ingest_packages_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..analytics.repo..", "..analytics.ingest..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule analytics_controller_and_web_adapters_must_not_depend_on_legacy_surfaces =
            noClasses()
                    .that().resideInAnyPackage("..analytics.controller..", "..analytics.infrastructure.web..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..analytics.service..",
                            "..analytics.repo..",
                            "..analytics.ingest..",
                            "..analytics.infrastructure.persistence..",
                            "..analytics.domain.."
                    )
                    .allowEmptyShould(true);
}
