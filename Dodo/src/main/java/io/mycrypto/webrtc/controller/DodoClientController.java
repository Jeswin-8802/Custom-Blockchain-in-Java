package io.mycrypto.webrtc.controller;

import io.mycrypto.webrtc.dto.StompMessage;
import io.mycrypto.webrtc.service.MessageProcessor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
public class DodoClientController implements StompSessionHandler {

    @Autowired
    private MessageProcessor messageProcessor;

    private StompSession stompSession = null;

    /**
     *  URL of server being connected to
     */
    private String serverURL = null;

    /**
     * Map of subscriptions.
     */
    @Getter
    private Map<String, StompSession.Subscription> subscriptions = new HashMap<>();

    /* --- StompSessionHandler methods --- */

    @Override
    public void afterConnected(@NotNull StompSession session, @NotNull StompHeaders connectedHeaders) {
        log.info("Connection to STOMP server established.\n" +
                "Session: {}\n" +
                "Headers: {}", session, connectedHeaders);
    }

    @Override
    public void handleException(@NotNull StompSession session, StompCommand command, @NotNull StompHeaders headers, @NotNull byte[] payload, @NotNull Throwable exception) {
        log.error("""
                Got an exception while handling a frame.
                Command: {}
                Headers: {}
                Payload: {}
                """,
                command, headers, payload, exception);
    }

    @Override
    public void handleTransportError(@NotNull StompSession session, @NotNull Throwable exception) {
        log.error("Retrieved a transport error: {}", session, exception);
        if (!session.isConnected()) {
            subscriptions.clear();
            connect(serverURL);
        }
    }

    @Override
    public Type getPayloadType(@NotNull StompHeaders headers) {
        return StompMessage.class;
    }

    @Override
    public void handleFrame(@NotNull StompHeaders headers, Object payload) {
        log.info("Got a new message {}", payload);
        try {
            messageProcessor.processMessageAsClient(stompSession.getSessionId() ,headers, (StompMessage) payload);
        } catch (ClassCastException exception) {
            log.error("Error occurred when casting payload to Class <StompMessage>", exception);
        }
    }

    /* ----------------------------------- */

    public void connect(String url) {
        serverURL = url;
        WebSocketClient client = new StandardWebSocketClient();
        WebSocketStompClient stompClient = new WebSocketStompClient(client);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        WebSocketHttpHeaders webSocketHeaders = new WebSocketHttpHeaders();
        StompHeaders stompHeaders = new StompHeaders();

        try {
            stompSession = stompClient
                    .connectAsync(
                            url,
                            webSocketHeaders,
                            stompHeaders,
                            this)
                    .get();
        } catch (Exception exception) {
            log.error("Connection failed.", exception); // TODO: Do some failover and implement retry patterns.
        }
    }

    public void sendMessage(String destination, StompMessage message) {
        if (stompSession != null)
            stompSession.send(destination, message);
    }

    public boolean isConnected() {
        return stompSession.isConnected();
    }

    public void subscribe(String uri) {
        log.info("Subscribing to: \"{}\"", uri);
        stompSession.subscribe(uri, this);
    }

    /**
     * Unsubscribe and close connection before destroying this instance.
     */
    void shutDown() {
        for (String key : subscriptions.keySet())
            subscriptions.get(key).unsubscribe();

        if (stompSession != null)
            stompSession.disconnect();
    }
}
