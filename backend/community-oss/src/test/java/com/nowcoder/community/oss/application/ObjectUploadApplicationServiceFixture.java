package com.nowcoder.community.oss.application;

import com.nowcoder.community.common.spring.policy.UploadPolicyDecisions;
import com.nowcoder.community.common.spring.policy.UploadPolicyProperties;
import com.nowcoder.community.oss.application.port.ObjectStorageSettings;
import com.nowcoder.community.oss.application.port.ObjectStore;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import com.nowcoder.community.oss.domain.repository.OssUploadSessionRepository;
import com.nowcoder.community.oss.domain.repository.OssUsagePolicyRepository;

import java.time.Clock;

final class ObjectUploadApplicationServiceFixture {

    private ObjectUploadApplicationServiceFixture() {
    }

    static Builder builder(
            OssObjectRepository objectRepository,
            OssObjectVersionRepository versionRepository,
            OssUploadSessionRepository uploadSessionRepository,
            ObjectStore objectStore
    ) {
        return new Builder(objectRepository, versionRepository, uploadSessionRepository, objectStore);
    }

    static final class Builder {

        private final OssObjectRepository objectRepository;
        private final OssObjectVersionRepository versionRepository;
        private final OssUploadSessionRepository uploadSessionRepository;
        private final ObjectStore objectStore;
        private OssUsagePolicyRepository policyRepository;
        private String storageBucket = "community-oss";
        private String publicBaseUrl = "http://localhost:12880";
        private Clock clock = Clock.systemUTC();
        private UploadPolicyDecisions uploadPolicyDecisions =
                new UploadPolicyDecisions(new UploadPolicyProperties());
        private boolean fileUploadEnabled = true;

        private Builder(
                OssObjectRepository objectRepository,
                OssObjectVersionRepository versionRepository,
                OssUploadSessionRepository uploadSessionRepository,
                ObjectStore objectStore
        ) {
            this.objectRepository = objectRepository;
            this.versionRepository = versionRepository;
            this.uploadSessionRepository = uploadSessionRepository;
            this.objectStore = objectStore;
        }

        Builder policyRepository(OssUsagePolicyRepository policyRepository) {
            this.policyRepository = policyRepository;
            return this;
        }

        Builder storageBucket(String storageBucket) {
            this.storageBucket = storageBucket;
            return this;
        }

        Builder publicBaseUrl(String publicBaseUrl) {
            this.publicBaseUrl = publicBaseUrl;
            return this;
        }

        Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        Builder uploadPolicyDecisions(UploadPolicyDecisions uploadPolicyDecisions) {
            this.uploadPolicyDecisions = uploadPolicyDecisions;
            return this;
        }

        Builder featureFlags(boolean fileUploadEnabled) {
            this.fileUploadEnabled = fileUploadEnabled;
            return this;
        }

        ObjectUploadApplicationService build() {
            ObjectStorageSettings settings = new FixtureObjectStorageSettings(publicBaseUrl, storageBucket);
            return new ObjectUploadApplicationService(
                    objectRepository,
                    versionRepository,
                    uploadSessionRepository,
                    policyRepository,
                    objectStore,
                    settings,
                    clock,
                    uploadPolicyDecisions,
                    fileUploadEnabled,
                    new ObjectUploadTransactionOperations(
                            objectRepository, versionRepository, uploadSessionRepository)
            );
        }
    }

    private record FixtureObjectStorageSettings(
            String publicBaseUrl,
            String storageBucket
    ) implements ObjectStorageSettings {
    }
}
