package com.nowcoder.community.social.application;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LikeApplicationOwnershipTest {

    private static final Set<String> FORBIDDEN_CONSTRUCTOR_TYPES = Set.of(
            "com.nowcoder.community.social.application.ContentEntityResolver",
            "com.nowcoder.community.content.api.query.ContentEntityQueryApi",
            "com.nowcoder.community.user.api.query.UserLookupQueryApi"
    );

    @Test
    void likeApplicationServiceShouldReceiveResolvedTargetWithoutForeignOwnerCollaborators() {
        assertThat(Arrays.stream(LikeApplicationService.class.getDeclaredConstructors())
                .map(Constructor::getParameterTypes)
                .flatMap(Arrays::stream)
                .map(Class::getName)
                .toList())
                .noneMatch(FORBIDDEN_CONSTRUCTOR_TYPES::contains);
    }

    @Test
    void legacyContentEntityResolverShouldBeRetiredFromSocialApplication() {
        assertThatThrownBy(() -> Class.forName(
                "com.nowcoder.community.social.application.ContentEntityResolver"
        )).isInstanceOf(ClassNotFoundException.class);
    }
}
