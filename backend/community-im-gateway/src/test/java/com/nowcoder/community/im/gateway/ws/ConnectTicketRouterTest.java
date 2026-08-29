package com.nowcoder.community.im.gateway.ws;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.im.common.ws.ConnectFrame;
import com.nowcoder.community.im.gateway.shard.WorkerRegistry;
import com.nowcoder.community.im.ticket.SessionTicketCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectTicketRouterTest {

    @Test
    void route_shouldNotRetainTicketValidationCause() {
        JacksonJsonCodec frameCodec = new JacksonJsonCodec(JacksonJsonCodec.standardMapper());
        SessionTicketCodec ticketCodec = mock(SessionTicketCodec.class);
        when(ticketCodec.decode("invalid-ticket")).thenThrow(new IllegalArgumentException("expired"));
        ConnectTicketRouter router = new ConnectTicketRouter(
                frameCodec,
                ticketCodec,
                new WorkerRegistry(List.of())
        );

        assertThatThrownBy(() -> router.route(frameCodec.toJson(new ConnectFrame("connect", "invalid-ticket"))))
                .isExactlyInstanceOf(ConnectTicketRouter.RoutingException.class)
                .hasMessage("invalid ticket")
                .hasNoCause();
    }
}
