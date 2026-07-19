package com.nowcoder.community.oss.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.oss.client.model.OssMetadataResponse;
import com.nowcoder.community.oss.client.model.OssPublicFileResponse;
import com.nowcoder.community.oss.client.model.OssBindReferenceRequest;
import com.nowcoder.community.oss.client.model.OssLifecycleResponse;
import com.nowcoder.community.oss.client.model.OssSignedUrlResponse;
import com.nowcoder.community.oss.client.model.OssReferenceResponse;
import com.nowcoder.community.oss.client.model.OssUploadSessionRequest;
import com.nowcoder.community.oss.client.model.OssUploadSessionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class CommunityOssClientContractTest {

    @Test
    void serviceTokenProviderShouldBeAMinimalFunctionalContract() {
        AtomicReference<Class<?>> providerType = new AtomicReference<>();
        Throwable lookupFailure = catchThrowable(() -> providerType.set(
                Class.forName("com.nowcoder.community.oss.client.OssServiceTokenProvider")));

        assertThat(lookupFailure).isNull();
        assertThat(providerType.get().isInterface()).isTrue();
        assertThat(providerType.get().isAnnotationPresent(FunctionalInterface.class)).isTrue();
        assertThat(Arrays.stream(providerType.get().getDeclaredMethods())
                .filter(method -> Modifier.isAbstract(method.getModifiers())))
                .singleElement()
                .satisfies(method -> {
                    assertThat(method.getName()).isEqualTo("tokenValue");
                    assertThat(method.getReturnType()).isEqualTo(String.class);
                    assertThat(method.getParameterCount()).isZero();
                });
    }

    @Test
    void internalClientShouldNotExposeUserGrantManagementCapabilities() {
        assertThat(Arrays.stream(CommunityOssClient.class.getMethods())
                .map(method -> method.getName()))
                .doesNotContain("grantObjectAccess", "revokeObjectAccess");
    }

    @Test
    void internalClientShouldNotPublishRetiredUserGrantModels() {
        assertThat(catchThrowable(() -> Class.forName(
                "com.nowcoder.community.oss.client.model.OssAccessDecisionResponse")))
                .isInstanceOf(ClassNotFoundException.class);
        assertThat(catchThrowable(() -> Class.forName(
                "com.nowcoder.community.oss.client.model.OssGrantObjectAccessRequest")))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void httpClientShouldAcceptAnExplicitServiceTokenProvider() {
        assertThat(hasPublicConstructor(String.class, OssServiceTokenProvider.class)).isTrue();
        assertThat(hasPublicConstructor(
                String.class,
                RestClient.Builder.class,
                OssServiceTokenProvider.class
        )).isTrue();
    }

    @Test
    void everyHttpClientConstructorShouldRequireAServiceTokenProvider() {
        assertThat(HttpCommunityOssClient.class.getConstructors()).allSatisfy(constructor ->
                assertThat(constructor.getParameterTypes()).contains(OssServiceTokenProvider.class));
    }

    @Test
    void clientShouldExposeStableUploadMetadataAndSignedUrlContracts() {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        UUID sessionId = uuid(3);
        OssUploadSessionRequest request = new OssUploadSessionRequest(
                "USER_AVATAR",
                "community-app",
                "user",
                "avatar",
                "7",
                "PUBLIC",
                "avatar.png",
                "image/png",
                6,
                "sha256-avatar",
                "7"
        );
        OssUploadSessionResponse upload = new OssUploadSessionResponse(
                sessionId,
                objectId,
                versionId,
                "PROXY",
                "/api/oss/objects/" + objectId + "/complete",
                Instant.parse("2026-05-07T00:15:00Z")
        );
        OssMetadataResponse metadata = new OssMetadataResponse(
                objectId,
                versionId,
                "USER_AVATAR",
                "community-app",
                "user",
                "avatar",
                "7",
                "PUBLIC",
                "ACTIVE",
                "avatar.png",
                "image/png",
                6,
                "sha256-avatar",
                "http://localhost:12880/files/" + objectId + "/" + versionId + "/avatar.png"
        );
        OssSignedUrlResponse signedUrl = new OssSignedUrlResponse(
                "http://localhost:12880/files/signed",
                "GET",
                Instant.parse("2026-05-07T00:05:00Z"),
                "private, max-age=300"
        );
        OssPublicFileResponse publicFile = new OssPublicFileResponse(
                "ok".getBytes(),
                "text/plain",
                2,
                "etag-1",
                "public, max-age=31536000, immutable",
                "avatar.png"
        );
        OssReferenceResponse reference = new OssReferenceResponse(
                uuid(5),
                objectId,
                versionId,
                "community-app",
                "user",
                "avatar",
                "7",
                "PRIMARY",
                "ACTIVE",
                null,
                Instant.parse("2026-05-07T00:00:00Z"),
                null
        );
        OssLifecycleResponse lifecycle = new OssLifecycleResponse(
                objectId,
                versionId,
                "PURGED",
                false,
                true,
                "object purged",
                Instant.parse("2026-05-07T00:10:00Z")
        );
        OssBindReferenceRequest bindRequest = new OssBindReferenceRequest(
                versionId.toString(),
                "community-app",
                "user",
                "avatar",
                "7",
                "PRIMARY",
                null,
                "7"
        );

        assertThat(CommunityOssClient.class).isNotNull();
        assertThat(request.usage()).isEqualTo("USER_AVATAR");
        assertThat(upload.uploadMode()).isEqualTo("PROXY");
        assertThat(metadata.currentVersionId()).isEqualTo(versionId);
        assertThat(signedUrl.method()).isEqualTo("GET");
        assertThat(publicFile.contentType()).isEqualTo("text/plain");
        assertThat(reference.status()).isEqualTo("ACTIVE");
        assertThat(lifecycle.purged()).isTrue();
        assertThat(bindRequest.referenceRole()).isEqualTo("PRIMARY");
        assertThat(bindRequest.referenceId()).isNull();
    }

    @Test
    void bindReferenceWireShouldCarryAnOptionalCallerSuppliedReferenceIdWithoutChangingExistingFields() {
        UUID referenceId = uuid(9);
        OssBindReferenceRequest request = new OssBindReferenceRequest(
                referenceId.toString(),
                uuid(2).toString(),
                "community-app",
                "content",
                "post-media",
                "post-7",
                "PRIMARY",
                Instant.parse("2026-05-07T01:00:00Z"),
                "actor-7"
        );

        JsonNode json = JsonMappers.standard().valueToTree(request);

        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "referenceId",
                "versionId",
                "subjectService",
                "subjectDomain",
                "subjectType",
                "subjectId",
                "referenceRole",
                "retainUntil",
                "actorId"
        );
        assertThat(json.path("referenceId").asText()).isEqualTo(referenceId.toString());
        assertThat(json.path("subjectDomain").asText()).isEqualTo("content");
        assertThat(json.path("referenceRole").asText()).isEqualTo("PRIMARY");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static boolean hasPublicConstructor(Class<?>... parameterTypes) {
        return Arrays.stream(HttpCommunityOssClient.class.getConstructors())
                .anyMatch(constructor -> Arrays.equals(constructor.getParameterTypes(), parameterTypes));
    }
}
