package com.nowcoder.community.app.contract;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.content.application.ContentEventDispatchApplicationService;
import com.nowcoder.community.content.infrastructure.event.OutboxContentEventPublisher;
import com.nowcoder.community.content.infrastructure.event.PostHotFeedProjectionKafkaListener;
import com.nowcoder.community.growth.infrastructure.event.TaskProgressEventBackboneKafkaListener;
import com.nowcoder.community.im.infrastructure.event.ImPolicyBackboneKafkaListener;
import com.nowcoder.community.notice.infrastructure.event.NoticeProjectionKafkaListener;
import com.nowcoder.community.search.infrastructure.event.SearchPostProjectionKafkaListener;
import com.nowcoder.community.social.application.SocialEventDispatchApplicationService;
import com.nowcoder.community.social.infrastructure.event.OutboxSocialDomainEventPublisher;
import com.nowcoder.community.social.infrastructure.event.SocialContentDeletionKafkaListener;
import com.nowcoder.community.user.application.UserEventDispatchApplicationService;
import com.nowcoder.community.user.infrastructure.event.OutboxUserPolicyEventPublisher;
import com.nowcoder.community.wallet.infrastructure.event.WalletRewardKafkaListener;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ContractEventCodecAdoptionTest {

    private static final String CONTENT_CODEC =
            "com.nowcoder.community.content.contracts.event.ContentContractEventCodec";
    private static final String SOCIAL_CODEC =
            "com.nowcoder.community.social.contracts.event.SocialContractEventCodec";
    private static final String USER_CODEC =
            "com.nowcoder.community.user.contracts.event.UserContractEventCodec";

    @ParameterizedTest(name = "{0} delegates owner wire conversion to {1}")
    @MethodSource("ownerEventBoundaryClasses")
    void ownerProducerAndDispatcherShouldUseTheCentralizedCodec(
            Class<?> boundaryType,
            String codecType
    ) {
        assertThat(dependencyTypeNames(boundaryType))
                .contains(codecType)
                .doesNotContain(JsonCodec.class.getName());
        assertThat(objectPayloadMethods(boundaryType))
                .as("generic Object payload methods in %s", boundaryType.getName())
                .isEmpty();
        assertThat(localPayloadRoutingMethods(boundaryType))
                .as("local type/payload routing in %s", boundaryType.getName())
                .isEmpty();
    }

    @ParameterizedTest(name = "{0} uses owner decoders {1}")
    @MethodSource("foreignEventListeners")
    void listenerShouldDecodeThroughOwnerContractsWithoutLocalObjectNormalization(
            Class<?> listenerType,
            Set<String> expectedCodecTypes
    ) {
        assertThat(dependencyTypeNames(listenerType))
                .containsAll(expectedCodecTypes)
                .doesNotContain(JsonCodec.class.getName());
        assertThat(objectPayloadMethods(listenerType))
                .as("Object payload methods in %s", listenerType.getName())
                .isEmpty();
        assertThat(localPayloadRoutingMethods(listenerType))
                .as("duplicate payload normalization in %s", listenerType.getName())
                .isEmpty();
    }

    private static Stream<Arguments> ownerEventBoundaryClasses() {
        return Stream.of(
                Arguments.of(OutboxContentEventPublisher.class, CONTENT_CODEC),
                Arguments.of(ContentEventDispatchApplicationService.class, CONTENT_CODEC),
                Arguments.of(OutboxSocialDomainEventPublisher.class, SOCIAL_CODEC),
                Arguments.of(SocialEventDispatchApplicationService.class, SOCIAL_CODEC),
                Arguments.of(OutboxUserPolicyEventPublisher.class, USER_CODEC),
                Arguments.of(UserEventDispatchApplicationService.class, USER_CODEC)
        );
    }

    private static Stream<Arguments> foreignEventListeners() {
        return Stream.of(
                Arguments.of(PostHotFeedProjectionKafkaListener.class, Set.of(CONTENT_CODEC, SOCIAL_CODEC)),
                Arguments.of(TaskProgressEventBackboneKafkaListener.class, Set.of(CONTENT_CODEC, SOCIAL_CODEC)),
                Arguments.of(NoticeProjectionKafkaListener.class, Set.of(CONTENT_CODEC, SOCIAL_CODEC)),
                Arguments.of(WalletRewardKafkaListener.class, Set.of(CONTENT_CODEC, SOCIAL_CODEC)),
                Arguments.of(SearchPostProjectionKafkaListener.class, Set.of(CONTENT_CODEC)),
                Arguments.of(SocialContentDeletionKafkaListener.class, Set.of(CONTENT_CODEC)),
                Arguments.of(ImPolicyBackboneKafkaListener.class, Set.of(USER_CODEC, SOCIAL_CODEC))
        );
    }

    private static Set<String> dependencyTypeNames(Class<?> type) {
        Stream<Class<?>> fieldTypes = Arrays.stream(type.getDeclaredFields()).map(field -> field.getType());
        Stream<Class<?>> constructorTypes = Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()));
        return Stream.concat(fieldTypes, constructorTypes)
                .map(Class::getName)
                .collect(Collectors.toSet());
    }

    private static Set<String> objectPayloadMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getReturnType().equals(Object.class)
                        || Arrays.asList(method.getParameterTypes()).contains(Object.class))
                .map(Method::toGenericString)
                .collect(Collectors.toSet());
    }

    private static Set<String> localPayloadRoutingMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().startsWith("normalize")
                        || method.getName().equals("typedPayload")
                        || method.getName().equals("requiredLikePayload"))
                .map(Method::toGenericString)
                .collect(Collectors.toSet());
    }
}
