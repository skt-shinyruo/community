package com.nowcoder.community.analytics.infrastructure.web;

import com.nowcoder.community.analytics.application.AnalyticsRequestCaptureApplicationService;
import com.nowcoder.community.analytics.application.AnalyticsRequestCaptureApplicationService.RequestObservation;
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
    void shouldCaptureRequestObservationAfterChain() throws Exception {
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        AnalyticsPrincipalResolver principalResolver = mock(AnalyticsPrincipalResolver.class);
        AnalyticsRequestCaptureApplicationService applicationService = mock(AnalyticsRequestCaptureApplicationService.class);
        AnalyticsRequestCaptureFilter filter = new AnalyticsRequestCaptureFilter(
                clientIpResolver,
                principalResolver,
                applicationService
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts/123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(200);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(clientIpResolver.resolve(request)).thenReturn(new ClientIpResolver.ResolvedClientIp("1.1.1.1", ClientIpResolver.SOURCE_REMOTE));
        when(principalResolver.resolveUserUuid(null)).thenReturn(userId);

        filter.doFilter(request, response, chain);

        verify(applicationService).capture(new RequestObservation(
                "GET",
                "/api/posts/123",
                200,
                "1.1.1.1",
                userId
        ));
    }

    @Test
    void shouldFailOpenWhenAnalyticsCaptureThrows() throws Exception {
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        AnalyticsPrincipalResolver principalResolver = mock(AnalyticsPrincipalResolver.class);
        AnalyticsRequestCaptureApplicationService applicationService = mock(AnalyticsRequestCaptureApplicationService.class);
        AnalyticsRequestCaptureFilter filter = new AnalyticsRequestCaptureFilter(
                clientIpResolver,
                principalResolver,
                applicationService
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts/123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        doThrow(new RuntimeException("capture failed")).when(applicationService).capture(new RequestObservation(
                "GET",
                "/api/posts/123",
                200,
                null,
                null
        ));

        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(applicationService).capture(new RequestObservation("GET", "/api/posts/123", 200, null, null));
    }

    @Test
    void shouldNotRecordWhenDownstreamRequestThrows() {
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        AnalyticsPrincipalResolver principalResolver = mock(AnalyticsPrincipalResolver.class);
        AnalyticsRequestCaptureApplicationService applicationService = mock(AnalyticsRequestCaptureApplicationService.class);
        AnalyticsRequestCaptureFilter filter = new AnalyticsRequestCaptureFilter(
                clientIpResolver,
                principalResolver,
                applicationService
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts/123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            throw new ServletException("downstream failed");
        })).isInstanceOf(ServletException.class);

        verifyNoInteractions(clientIpResolver, principalResolver, applicationService);
    }
}
