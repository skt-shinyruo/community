package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SynchronousCollaborationBaselineArchTest {

    private static final Pattern OWNED_PACKAGE = Pattern.compile(
            "^com\\.nowcoder\\.community\\.([^.]+)\\.(.+)$"
    );

    private static final Set<String> EXPECTED_FOREIGN_API_EDGES = Set.of(
            "com.nowcoder.community.auth.application.LoginApplicationService -> com.nowcoder.community.analytics.api.action.AnalyticsIngestActionApi",
            "com.nowcoder.community.auth.application.LoginApplicationService -> com.nowcoder.community.user.api.model.UserAuthenticationResultView",
            "com.nowcoder.community.auth.application.LoginApplicationService -> com.nowcoder.community.user.api.model.UserAuthenticationResultView$Failure",
            "com.nowcoder.community.auth.application.LoginApplicationService -> com.nowcoder.community.user.api.model.UserCredentialView",
            "com.nowcoder.community.auth.application.LoginApplicationService -> com.nowcoder.community.user.api.query.UserCredentialQueryApi",
            "com.nowcoder.community.auth.application.LoginTokenIssuer -> com.nowcoder.community.user.api.model.UserCredentialView",
            "com.nowcoder.community.auth.application.LoginTokenIssuer -> com.nowcoder.community.user.api.query.UserCredentialQueryApi",
            "com.nowcoder.community.auth.application.PasswordResetApplicationService -> com.nowcoder.community.user.api.action.UserCredentialActionApi",
            "com.nowcoder.community.auth.application.PasswordResetApplicationService -> com.nowcoder.community.user.api.model.UserCredentialView",
            "com.nowcoder.community.auth.application.PasswordResetApplicationService -> com.nowcoder.community.user.api.query.UserCredentialQueryApi",
            "com.nowcoder.community.auth.application.RegistrationApplicationService -> com.nowcoder.community.user.api.action.UserRegistrationActionApi",
            "com.nowcoder.community.auth.application.RegistrationApplicationService -> com.nowcoder.community.user.api.model.PreparedRegistrationUserView",
            "com.nowcoder.community.auth.application.RegistrationVerificationApplicationService -> com.nowcoder.community.user.api.action.UserRegistrationActionApi",
            "com.nowcoder.community.auth.application.RegistrationVerificationApplicationService -> com.nowcoder.community.user.api.model.UserCredentialView",
            "com.nowcoder.community.auth.application.RegistrationVerificationApplicationService -> com.nowcoder.community.user.api.model.VerifiedRegistrationUserCommand",
            "com.nowcoder.community.auth.application.TokenFreshnessApplicationService -> com.nowcoder.community.user.api.model.UserCredentialView",
            "com.nowcoder.community.auth.application.TokenFreshnessApplicationService -> com.nowcoder.community.user.api.query.UserCredentialQueryApi",
            "com.nowcoder.community.content.application.CommentApplicationService -> com.nowcoder.community.social.api.query.SocialBlockQueryApi",
            "com.nowcoder.community.content.application.FollowFeedReadApplicationService -> com.nowcoder.community.social.api.query.SocialFollowQueryApi",
            "com.nowcoder.community.content.application.ModerationApplicationService -> com.nowcoder.community.user.api.action.UserModerationActionApi",
            "com.nowcoder.community.content.application.UserModerationGuard -> com.nowcoder.community.user.api.model.UserModerationStateView",
            "com.nowcoder.community.content.application.UserModerationGuard -> com.nowcoder.community.user.api.query.UserModerationQueryApi",
            "com.nowcoder.community.growth.application.TaskProgressApplicationService -> com.nowcoder.community.wallet.api.action.WalletRewardActionApi",
            "com.nowcoder.community.im.application.ImPolicySnapshotApplicationService -> com.nowcoder.community.social.api.model.SocialBlockRelationView",
            "com.nowcoder.community.im.application.ImPolicySnapshotApplicationService -> com.nowcoder.community.social.api.query.SocialBlockQueryApi",
            "com.nowcoder.community.im.application.ImPolicySnapshotApplicationService -> com.nowcoder.community.user.api.model.UserModerationStateView",
            "com.nowcoder.community.im.application.ImPolicySnapshotApplicationService -> com.nowcoder.community.user.api.query.UserLookupQueryApi",
            "com.nowcoder.community.im.application.ImPolicySnapshotApplicationService -> com.nowcoder.community.user.api.query.UserModerationQueryApi",
            "com.nowcoder.community.interaction.application.LikeInteractionApplicationService -> com.nowcoder.community.content.api.model.ResolvedContentRef",
            "com.nowcoder.community.interaction.application.LikeInteractionApplicationService -> com.nowcoder.community.content.api.query.ContentEntityQueryApi",
            "com.nowcoder.community.interaction.application.LikeInteractionApplicationService -> com.nowcoder.community.social.api.action.SocialLikeActionApi",
            "com.nowcoder.community.interaction.application.LikeInteractionApplicationService -> com.nowcoder.community.social.api.model.ResolvedLikeTargetView",
            "com.nowcoder.community.interaction.application.LikeInteractionApplicationService -> com.nowcoder.community.social.api.model.SocialLikeResultView",
            "com.nowcoder.community.interaction.application.LikeInteractionApplicationService -> com.nowcoder.community.user.api.model.UserSummaryView",
            "com.nowcoder.community.interaction.application.LikeInteractionApplicationService -> com.nowcoder.community.user.api.query.UserLookupQueryApi",
            "com.nowcoder.community.market.application.MarketWalletActionProcessorApplicationService -> com.nowcoder.community.wallet.api.action.WalletMarketActionApi",
            "com.nowcoder.community.market.application.MarketWalletActionProcessorApplicationService -> com.nowcoder.community.wallet.api.model.WalletMarketTxnView",
            "com.nowcoder.community.ops.application.HotCacheGovernanceApplicationService -> com.nowcoder.community.content.api.action.HotFeedCacheGovernanceActionApi",
            "com.nowcoder.community.ops.application.HotCacheGovernanceApplicationService -> com.nowcoder.community.content.api.model.HotFeedCachePrewarmRequest",
            "com.nowcoder.community.ops.application.HotCacheGovernanceApplicationService -> com.nowcoder.community.content.api.model.HotFeedCachePrewarmResultView",
            "com.nowcoder.community.ops.application.HotCacheGovernanceApplicationService -> com.nowcoder.community.content.api.model.HotFeedCacheStatusView",
            "com.nowcoder.community.ops.application.HotCacheGovernanceApplicationService -> com.nowcoder.community.content.api.model.HotFeedDegradationSignalView",
            "com.nowcoder.community.ops.application.HotCacheGovernanceApplicationService -> com.nowcoder.community.content.api.model.UpdateHotFeedDegradationSignalRequest",
            "com.nowcoder.community.ops.application.HotCacheGovernanceApplicationService -> com.nowcoder.community.content.api.query.HotFeedCacheGovernanceQueryApi",
            "com.nowcoder.community.profile.application.UserProfileQueryApplicationService -> com.nowcoder.community.content.api.model.PostSummaryView",
            "com.nowcoder.community.profile.application.UserProfileQueryApplicationService -> com.nowcoder.community.content.api.model.RecentUserCommentView",
            "com.nowcoder.community.profile.application.UserProfileQueryApplicationService -> com.nowcoder.community.content.api.query.PostReadQueryApi",
            "com.nowcoder.community.profile.application.UserProfileQueryApplicationService -> com.nowcoder.community.growth.api.model.UserLevelSummaryView",
            "com.nowcoder.community.profile.application.UserProfileQueryApplicationService -> com.nowcoder.community.growth.api.query.UserLevelQueryApi",
            "com.nowcoder.community.profile.application.UserProfileQueryApplicationService -> com.nowcoder.community.social.api.query.SocialFollowQueryApi",
            "com.nowcoder.community.profile.application.UserProfileQueryApplicationService -> com.nowcoder.community.social.api.query.SocialLikeQueryApi",
            "com.nowcoder.community.profile.application.UserProfileQueryApplicationService -> com.nowcoder.community.user.api.model.UserProfileView",
            "com.nowcoder.community.profile.application.UserProfileQueryApplicationService -> com.nowcoder.community.user.api.query.UserProfileQueryApi",
            "com.nowcoder.community.search.application.PostSearchPayloadAssembler -> com.nowcoder.community.content.api.model.PostScanView$PostProjectionView",
            "com.nowcoder.community.search.application.SearchPostProjectionApplicationService -> com.nowcoder.community.content.api.model.PostScanView$PostProjectionView",
            "com.nowcoder.community.search.application.SearchPostProjectionApplicationService -> com.nowcoder.community.content.api.query.PostScanQueryApi",
            "com.nowcoder.community.social.application.FollowApplicationService -> com.nowcoder.community.user.api.query.UserLookupQueryApi",
            "com.nowcoder.community.wallet.application.WalletAdminOpsApplicationService -> com.nowcoder.community.user.api.query.UserLookupQueryApi",
            "com.nowcoder.community.wallet.application.WalletTransferApplicationService -> com.nowcoder.community.user.api.query.UserLookupQueryApi"
    );

    @Test
    void applicationForeignApiEdgesMustMatchTheReviewedBaseline() {
        Set<String> actualEdges = new TreeSet<>();
        for (JavaClass origin : new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.nowcoder.community")) {
            String originDomain = domain(origin);
            if (originDomain.isEmpty() || !isApplication(origin)) {
                continue;
            }
            for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                String targetDomain = domain(target);
                if (targetDomain.isEmpty() || originDomain.equals(targetDomain) || !isPublishedApi(target)) {
                    continue;
                }
                actualEdges.add(origin.getFullName() + " -> " + target.getFullName());
            }
        }

        assertThat(actualEdges)
                .as("reviewed synchronous foreign API edges")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_FOREIGN_API_EDGES);
    }

    @Test
    void coreApplicationSynchronousCollaborationGraphMustBeAcyclic() {
        Map<String, Set<String>> graph = new TreeMap<>();
        for (JavaClass origin : new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.nowcoder.community")) {
            String originDomain = domain(origin);
            if (!ArchitectureRulesSupport.CORE_DOMAINS.contains(originDomain) || !isApplication(origin)) {
                continue;
            }
            graph.computeIfAbsent(originDomain, ignored -> new TreeSet<>());
            for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                String targetDomain = domain(target);
                if (targetDomain.isEmpty() || originDomain.equals(targetDomain) || !isPublishedApi(target)) {
                    continue;
                }
                graph.computeIfAbsent(targetDomain, ignored -> new TreeSet<>());
                graph.get(originDomain).add(targetDomain);
            }
        }

        assertThat(findCycles(graph))
                .as("core synchronous owner-domain graph must not contain strongly connected cycles")
                .isEmpty();
    }

    private static List<String> findCycles(Map<String, Set<String>> graph) {
        Map<String, VisitState> states = new TreeMap<>();
        graph.keySet().forEach(node -> states.put(node, VisitState.UNVISITED));
        List<String> cycles = new ArrayList<>();
        Deque<String> path = new ArrayDeque<>();
        for (String node : graph.keySet()) {
            if (states.get(node) == VisitState.UNVISITED) {
                findCyclesFrom(node, graph, states, path, cycles);
            }
        }
        return cycles;
    }

    private static void findCyclesFrom(
            String node,
            Map<String, Set<String>> graph,
            Map<String, VisitState> states,
            Deque<String> path,
            List<String> cycles
    ) {
        states.put(node, VisitState.VISITING);
        path.addLast(node);
        for (String target : graph.getOrDefault(node, Set.of())) {
            VisitState targetState = states.getOrDefault(target, VisitState.UNVISITED);
            if (targetState == VisitState.UNVISITED) {
                findCyclesFrom(target, graph, states, path, cycles);
                continue;
            }
            if (targetState == VisitState.VISITING) {
                List<String> currentPath = new ArrayList<>(path);
                int cycleStart = currentPath.indexOf(target);
                List<String> cycle = new ArrayList<>(currentPath.subList(cycleStart, currentPath.size()));
                cycle.add(target);
                cycles.add(String.join(" -> ", cycle));
            }
        }
        path.removeLast();
        states.put(node, VisitState.VISITED);
    }

    private static boolean isApplication(JavaClass type) {
        Matcher matcher = OWNED_PACKAGE.matcher(type.getPackageName());
        return matcher.matches()
                && (matcher.group(2).equals("application") || matcher.group(2).startsWith("application."));
    }

    private static boolean isPublishedApi(JavaClass type) {
        Matcher matcher = OWNED_PACKAGE.matcher(type.getPackageName());
        if (!matcher.matches()) {
            return false;
        }
        String ownedPackage = matcher.group(2);
        return ownedPackage.equals("api.query")
                || ownedPackage.startsWith("api.query.")
                || ownedPackage.equals("api.action")
                || ownedPackage.startsWith("api.action.")
                || ownedPackage.equals("api.model")
                || ownedPackage.startsWith("api.model.");
    }

    private static String domain(JavaClass type) {
        Matcher matcher = OWNED_PACKAGE.matcher(type.getPackageName());
        return matcher.matches() ? matcher.group(1) : "";
    }

    private enum VisitState {
        UNVISITED,
        VISITING,
        VISITED
    }
}
