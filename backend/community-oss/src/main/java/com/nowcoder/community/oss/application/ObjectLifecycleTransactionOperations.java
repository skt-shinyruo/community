package com.nowcoder.community.oss.application;

import com.nowcoder.community.oss.domain.model.OssAccessGrant;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectReference;
import com.nowcoder.community.oss.domain.model.OssObjectStatus;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.repository.OssAccessGrantRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectReferenceRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class ObjectLifecycleTransactionOperations {

    private final OssObjectRepository objectRepository;
    private final OssObjectVersionRepository versionRepository;
    private final OssObjectReferenceRepository referenceRepository;
    private final OssAccessGrantRepository grantRepository;

    public ObjectLifecycleTransactionOperations(
            OssObjectRepository objectRepository,
            OssObjectVersionRepository versionRepository,
            OssObjectReferenceRepository referenceRepository,
            OssAccessGrantRepository grantRepository
    ) {
        this.objectRepository = Objects.requireNonNull(objectRepository, "objectRepository must not be null");
        this.versionRepository = Objects.requireNonNull(versionRepository, "versionRepository must not be null");
        this.referenceRepository = Objects.requireNonNull(referenceRepository, "referenceRepository must not be null");
        this.grantRepository = Objects.requireNonNull(grantRepository, "grantRepository must not be null");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ObjectDeletionClaimResult claimDeletion(UUID objectId, Instant now) {
        return claimDeletionCore(objectId, now);
    }

    private ObjectDeletionClaimResult claimDeletionCore(UUID objectId, Instant now) {
        OssObject object = objectRepository.findById(objectId)
                .orElseThrow(() -> new IllegalStateException("object disappeared before delete claim"));
        if (object.status() == OssObjectStatus.PURGED) {
            return new ObjectDeletionClaimResult(object, null);
        }
        OssObjectVersion currentVersion = findCurrentVersion(object);

        boolean blocked = activeReferences(object.objectId(), now)
                || activeGrants(object.objectId(), now);
        OssObject deletePending = object.status() == OssObjectStatus.DELETE_PENDING && !blocked
                ? object
                : object.deletePending(now);
        if (deletePending != object) {
            objectRepository.save(deletePending);
        }
        if (blocked) {
            return new ObjectDeletionClaimResult(deletePending, null);
        }
        if (currentVersion == null) {
            OssObject purged = deletePending.purge(now);
            objectRepository.save(purged);
            return new ObjectDeletionClaimResult(purged, null);
        }
        return new ObjectDeletionClaimResult(
                deletePending,
                new ObjectDeletionClaim(
                        deletePending,
                        currentVersion.storageBucket(),
                        currentVersion.storageKey()
                )
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ObjectDeletionClaim> claimRecoverableDeletion(UUID objectId, Instant now) {
        return claimDeletionCore(objectId, now).claimedDeletion();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OssObject finalizeDeletion(ObjectDeletionClaim claim, Instant now) {
        OssObject object = objectRepository.findById(claim.object().objectId())
                .orElseThrow(() -> new IllegalStateException("object disappeared before delete finalize"));
        if (object.status() == OssObjectStatus.PURGED) {
            return object;
        }
        if (object.status() != OssObjectStatus.DELETE_PENDING
                || !Objects.equals(object.currentVersionId(), claim.object().currentVersionId())) {
            throw new IllegalStateException("object delete claim is no longer current");
        }
        if (activeReferences(object.objectId(), now) || activeGrants(object.objectId(), now)) {
            throw new IllegalStateException("object gained an active dependency during deletion");
        }

        OssObjectVersion currentVersion = findCurrentVersion(object);
        if (currentVersion != null) {
            if (!currentVersion.storageBucket().equals(claim.storageBucket())
                    || !currentVersion.storageKey().equals(claim.storageKey())) {
                throw new IllegalStateException("object version changed during deletion");
            }
            versionRepository.save(currentVersion.purge(now));
        }
        OssObject purged = object.purge(now);
        objectRepository.save(purged);
        return purged;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<UUID> listRecoverableDeletionIds(Instant updatedBefore, int limit) {
        if (updatedBefore == null || limit <= 0) {
            return List.of();
        }
        List<UUID> objectIds = objectRepository.listDeletePendingIds(updatedBefore, limit);
        return objectIds == null ? List.of() : List.copyOf(objectIds);
    }

    private OssObjectVersion findCurrentVersion(OssObject object) {
        UUID currentVersionId = object.currentVersionId();
        if (currentVersionId == null) {
            return null;
        }
        OssObjectVersion version = versionRepository.findById(currentVersionId)
                .orElseThrow(() -> new ObjectDeletionTargetNotFoundException(
                        "current object version is missing"));
        if (!object.objectId().equals(version.objectId())) {
            throw new ObjectDeletionTargetNotFoundException(
                    "current object version belongs to a different object");
        }
        return version;
    }

    private boolean activeReferences(UUID objectId, Instant now) {
        List<OssObjectReference> references = referenceRepository.findByObjectId(objectId);
        return references != null && references.stream().anyMatch(reference -> reference.activeAt(now));
    }

    private boolean activeGrants(UUID objectId, Instant now) {
        List<OssAccessGrant> grants = grantRepository.findByObjectId(objectId);
        return grants != null && grants.stream().anyMatch(grant -> grant.activeAt(now));
    }
}
