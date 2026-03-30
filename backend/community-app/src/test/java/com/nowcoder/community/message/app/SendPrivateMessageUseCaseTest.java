package com.nowcoder.community.message.app;

import com.nowcoder.community.infra.idempotency.IdempotencyGuard;
import com.nowcoder.community.message.dto.SendMessageRequest;
import com.nowcoder.community.message.service.PrivateMessageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SendPrivateMessageUseCaseTest {

    @Test
    void useCaseShouldOnlyExposeMessageBoundaryConstructor() {
        assertThat(SendPrivateMessageUseCase.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        PrivateMessageService.class,
                        MessageRecipientResolver.class,
                        IdempotencyGuard.class
                ));
    }

    @Test
    void sendShouldWrapRecipientResolutionAndDispatchInsideIdempotencyGuard() {
        PrivateMessageService privateMessageService = mock(PrivateMessageService.class);
        MessageRecipientResolver recipientResolver = mock(MessageRecipientResolver.class);
        IdempotencyGuard idempotencyGuard = mock(IdempotencyGuard.class);
        SendPrivateMessageUseCase useCase = new SendPrivateMessageUseCase(
                privateMessageService,
                recipientResolver,
                idempotencyGuard
        );

        Authentication authentication = authentication(7);
        SendMessageRequest request = new SendMessageRequest();
        request.setToName("alice");
        request.setContent("hello");
        when(recipientResolver.resolveToUserId(request)).thenReturn(9);

        useCase.send(authentication, "idem-1", request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Supplier<Void>> supplierCaptor = ArgumentCaptor.forClass(Supplier.class);
        verify(idempotencyGuard).executeRequired(
                eq("message:send_message"),
                eq(7),
                eq("idem-1"),
                eq(Void.class),
                supplierCaptor.capture()
        );
        verifyNoInteractions(recipientResolver, privateMessageService);

        supplierCaptor.getValue().get();

        verify(recipientResolver).resolveToUserId(request);
        verify(privateMessageService).send(7, 9, "hello");
    }

    private Authentication authentication(int userId) {
        Jwt jwt = Jwt.withTokenValue("token-" + userId)
                .header("alg", "none")
                .subject(String.valueOf(userId))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        return authentication;
    }
}
