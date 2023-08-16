package io.mycrypto.config;

import lombok.NonNull;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

public class AssignPrincipalHandshakeHandler extends DefaultHandshakeHandler {
    private static final String ATTR_PRINCIPAL = "__principal__";

    @Override
    protected Principal determineUser(@NonNull final ServerHttpRequest request, @NonNull final WebSocketHandler wsHandler, Map<String, Object> attributes) {
        final String name;
        System.out.println("---" + request.getPrincipal() + "\n" + request.getHeaders() + "\n" + attributes);
        if (!attributes.containsKey(ATTR_PRINCIPAL)) {
            name = String.valueOf(UUID.randomUUID());
            attributes.put(ATTR_PRINCIPAL, name);
        } else {
            name = (String) attributes.get(ATTR_PRINCIPAL);
        }
        return () -> name;
    }
}
