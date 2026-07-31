package org.springframework.web.reactive.function.client;

import java.net.URI;
import java.util.Objects;

public interface ExchangeFunction {
    Object exchange(Object request);

    record Request(String method, URI url) {
        public Request {
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(url, "url");
        }
    }

    final class Fixture implements ExchangeFunction {
        @Override
        public Object exchange(Object request) {
            return "http-ok";
        }
    }
}
