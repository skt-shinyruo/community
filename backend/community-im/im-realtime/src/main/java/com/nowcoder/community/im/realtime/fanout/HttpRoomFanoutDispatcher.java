package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.im.realtime.client.ImServiceClientProperties;
import com.nowcoder.community.im.realtime.session.ImSessionProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

@Component
public class HttpRoomFanoutDispatcher implements RoomFanoutDispatcher {

    private final RealtimeWorkerDirectory workerDirectory;
    private final RoomFanoutTargetService localTargetService;
    private final WebClient webClient;
    private final RoomFanoutProperties fanoutProperties;
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String subject;
    private final String internalScope;
    private final String localWorkerId;

    public HttpRoomFanoutDispatcher(
            RealtimeWorkerDirectory workerDirectory,
            RoomFanoutTargetService localTargetService,
            RoomFanoutProperties fanoutProperties,
            JwtProperties jwtProperties,
            ImServiceClientProperties clientProperties,
            ImSessionProperties sessionProperties
    ) {
        this.workerDirectory = workerDirectory;
        this.localTargetService = localTargetService;
        this.webClient = WebClient.builder().build();
        this.fanoutProperties = fanoutProperties;
        this.jwtEncoder = JwtCodecs.jwtEncoder(jwtProperties);
        this.issuer = JwtCodecs.resolvedIssuer(jwtProperties);
        this.subject = StringUtils.hasText(sessionProperties.getWorkerId())
                ? sessionProperties.getWorkerId().trim()
                : "im-realtime";
        this.internalScope = clientProperties.getInternalScope();
        this.localWorkerId = this.subject;
    }

    @Override
    public void dispatch(RoomFanoutCommand command) {
        if (command == null || !StringUtils.hasText(command.targetWorkerId())) {
            return;
        }
        if (localWorkerId.equals(command.targetWorkerId().trim())) {
            RoomFanoutTargetResult result = localTargetService.apply(command);
            if (result != RoomFanoutTargetResult.ACCEPTED && result != RoomFanoutTargetResult.DUPLICATE) {
                throw new IllegalStateException("Local room fanout command rejected: " + result);
            }
            return;
        }
        RealtimeWorkerEndpoint endpoint = workerDirectory.find(command.targetWorkerId())
                .orElseThrow(() -> new IllegalStateException("Realtime worker not found: " + command.targetWorkerId()));
        webClient.post()
                .uri(endpoint.uri())
                .header(HttpHeaders.AUTHORIZATION, internalBearer())
                .bodyValue(command)
                .retrieve()
                .toBodilessEntity()
                .timeout(fanoutProperties.normalizedTargetTimeout())
                .block(fanoutProperties.normalizedTargetTimeout().plusMillis(100));
    }

    private String internalBearer() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("scope", internalScope)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
