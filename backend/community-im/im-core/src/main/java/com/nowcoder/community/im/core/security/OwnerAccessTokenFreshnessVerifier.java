package com.nowcoder.community.im.core.security;

import com.nowcoder.community.im.core.application.AccessTokenFreshnessApplicationService.Decision;
import com.nowcoder.community.im.core.application.AccessTokenFreshnessApplicationService.OwnerVerifier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OwnerAccessTokenFreshnessVerifier implements OwnerVerifier {

    private final RestClient restClient;

    public OwnerAccessTokenFreshnessVerifier(
            @Qualifier("imAccessTokenFreshnessRestClient") RestClient restClient
    ) {
        this.restClient = restClient;
    }

    @Override
    public Decision verify(String accessToken) {
        try {
            return restClient.get()
                    .uri("/api/auth/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .exchange((request, response) -> decision(response.getStatusCode().value()));
        } catch (RestClientException exception) {
            return Decision.UNAVAILABLE;
        }
    }

    private Decision decision(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return Decision.FRESH;
        }
        if (statusCode == HttpStatus.UNAUTHORIZED.value()) {
            return Decision.STALE;
        }
        if (statusCode == HttpStatus.FORBIDDEN.value()) {
            return Decision.DENIED;
        }
        return Decision.UNAVAILABLE;
    }
}
