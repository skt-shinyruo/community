package com.nowcoder.community.analytics.infrastructure.web;

import com.nowcoder.community.analytics.application.AnalyticsIngestApplicationService;
import com.nowcoder.community.analytics.application.AnalyticsRequestCaptureApplicationService;
import com.nowcoder.community.analytics.application.AnalyticsRequestCapturePort;
import com.nowcoder.community.analytics.application.command.RecordRequestCommand;
import com.nowcoder.community.analytics.infrastructure.event.AnalyticsRequestEventPublisher;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalyticsRequestCaptureFilterTest {

    @Test
    void shouldRecordEligibleRequestAfterChain() throws Exception {
        AnalyticsRequestClassifier classifier = mock(AnalyticsRequestClassifier.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        AnalyticsPrincipalResolver principalResolver = mock(AnalyticsPrincipalResolver.class);
        AnalyticsIngestProperties properties = enabledProperties();
        AnalyticsIngestApplicationService ingestService = mock(AnalyticsIngestApplicationService.class);
        AnalyticsRequestEventPublisher eventPublisher = mock(AnalyticsRequestEventPublisher.class);
        AnalyticsRequestCaptureFilter filter = new AnalyticsRequestCaptureFilter(
                classifier,
                clientIpResolver,
                principalResolver,
                properties,
                new AnalyticsRequestCaptureApplicationService(ingestService, eventPublisher)
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts/123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(200);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(classifier.classify("GET", "/api/posts/123", 200))
                .thenReturn(new AnalyticsRequestClassifier.Decision(true, "/api/posts/{id}", "captured"));
        when(clientIpResolver.resolve(request)).thenReturn(new ClientIpResolver.ResolvedClientIp("1.1.1.1", ClientIpResolver.SOURCE_REMOTE));
        when(principalResolver.resolveUserUuid(null)).thenReturn(userId);

        filter.doFilter(request, response, chain);

        verify(ingestService).recordRequest(new RecordRequestCommand("1.1.1.1", userId, true, true));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void filterShouldPublishAnalyticsEventInsteadOfCallingServiceDirectlyWhenAsyncEnabled() throws Exception {
        AnalyticsRequestClassifier classifier = mock(AnalyticsRequestClassifier.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        AnalyticsPrincipalResolver principalResolver = mock(AnalyticsPrincipalResolver.class);
        AnalyticsIngestProperties properties = enabledProperties();
        properties.setAsyncEnabled(true);
        AnalyticsIngestApplicationService ingestService = mock(AnalyticsIngestApplicationService.class);
        AnalyticsRequestEventPublisher eventPublisher = mock(AnalyticsRequestEventPublisher.class);
        AnalyticsRequestCaptureFilter filter = new AnalyticsRequestCaptureFilter(
                classifier,
                clientIpResolver,
                principalResolver,
                properties,
                new AnalyticsRequestCaptureApplicationService(ingestService, eventPublisher)
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts/123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(classifier.classify("GET", "/api/posts/123", 200))
                .thenReturn(new AnalyticsRequestClassifier.Decision(true, "/api/posts/{id}", "captured"));
        when(clientIpResolver.resolve(request)).thenReturn(new ClientIpResolver.ResolvedClientIp("1.1.1.1", ClientIpResolver.SOURCE_REMOTE));
        when(principalResolver.resolveUserUuid(null)).thenReturn(userId);

        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));

        verify(eventPublisher).publish(new RecordRequestCommand("1.1.1.1", userId, true, true));
        verifyNoInteractions(ingestService);
    }

    @Test
    void filterShouldFallBackToSyncWhenAsyncEnabledButPublisherUnavailable() throws Exception {
        AnalyticsRequestClassifier classifier = mock(AnalyticsRequestClassifier.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        AnalyticsPrincipalResolver principalResolver = mock(AnalyticsPrincipalResolver.class);
        AnalyticsIngestProperties properties = enabledProperties();
        properties.setAsyncEnabled(true);
        AnalyticsIngestApplicationService ingestService = mock(AnalyticsIngestApplicationService.class);
        AnalyticsRequestCaptureFilter filter = new AnalyticsRequestCaptureFilter(
                classifier,
                clientIpResolver,
                principalResolver,
                properties,
                new AnalyticsRequestCaptureApplicationService(ingestService, (AnalyticsRequestCapturePort) null)
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts/123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(classifier.classify("GET", "/api/posts/123", 200))
                .thenReturn(new AnalyticsRequestClassifier.Decision(true, "/api/posts/{id}", "captured"));
        when(clientIpResolver.resolve(request)).thenReturn(new ClientIpResolver.ResolvedClientIp("1.1.1.1", ClientIpResolver.SOURCE_REMOTE));
        when(principalResolver.resolveUserUuid(null)).thenReturn(userId);

        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));

        verify(ingestService).recordRequest(new RecordRequestCommand("1.1.1.1", userId, true, true));
    }

    @Test
    void shouldSkipWhenClassifierSkips() throws Exception {
        AnalyticsRequestClassifier classifier = mock(AnalyticsRequestClassifier.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        AnalyticsPrincipalResolver principalResolver = mock(AnalyticsPrincipalResolver.class);
        AnalyticsIngestApplicationService ingestService = mock(AnalyticsIngestApplicationService.class);
        AnalyticsRequestEventPublisher eventPublisher = mock(AnalyticsRequestEventPublisher.class);
        AnalyticsRequestCaptureFilter filter = new AnalyticsRequestCaptureFilter(
                classifier,
                clientIpResolver,
                principalResolver,
                enabledProperties(),
                new AnalyticsRequestCaptureApplicationService(ingestService, eventPublisher)
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/analytics/uv");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(classifier.classify("GET", "/api/analytics/uv", 200))
                .thenReturn(new AnalyticsRequestClassifier.Decision(false, null, "excluded_path"));

        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));

        verifyNoInteractions(ingestService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldFailOpenWhenAnalyticsCaptureThrows() throws Exception {
        AnalyticsRequestClassifier classifier = mock(AnalyticsRequestClassifier.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        AnalyticsPrincipalResolver principalResolver = mock(AnalyticsPrincipalResolver.class);
        AnalyticsIngestApplicationService ingestService = mock(AnalyticsIngestApplicationService.class);
        AnalyticsRequestEventPublisher eventPublisher = mock(AnalyticsRequestEventPublisher.class);
        AnalyticsRequestCaptureFilter filter = new AnalyticsRequestCaptureFilter(
                classifier,
                clientIpResolver,
                principalResolver,
                enabledProperties(),
                new AnalyticsRequestCaptureApplicationService(ingestService, eventPublisher)
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts/123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        doThrow(new RuntimeException("classifier failed")).when(classifier).classify("GET", "/api/posts/123", 200);

        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));

        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(ingestService, eventPublisher);
    }

    @Test
    void shouldNotRecordWhenDownstreamRequestThrows() {
        AnalyticsRequestClassifier classifier = mock(AnalyticsRequestClassifier.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        AnalyticsPrincipalResolver principalResolver = mock(AnalyticsPrincipalResolver.class);
        AnalyticsIngestApplicationService ingestService = mock(AnalyticsIngestApplicationService.class);
        AnalyticsRequestEventPublisher eventPublisher = mock(AnalyticsRequestEventPublisher.class);
        AnalyticsRequestCaptureFilter filter = new AnalyticsRequestCaptureFilter(
                classifier,
                clientIpResolver,
                principalResolver,
                enabledProperties(),
                new AnalyticsRequestCaptureApplicationService(ingestService, eventPublisher)
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts/123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            throw new ServletException("downstream failed");
        })).isInstanceOf(ServletException.class);

        verifyNoInteractions(classifier, clientIpResolver, principalResolver, ingestService, eventPublisher);
    }

    private AnalyticsIngestProperties enabledProperties() {
        AnalyticsIngestProperties properties = new AnalyticsIngestProperties();
        properties.setEnabled(true);
        properties.setAsyncEnabled(false);
        return properties;
    }
}
