package io.mycrypto.webrtc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mycrypto.webrtc.controller.DodoClientController;
import io.mycrypto.webrtc.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.mycrypto.webrtc.dto.MessageType.ICE_OFFER;
import static io.mycrypto.webrtc.dto.MessageType.ICE_REQUEST;

@Slf4j
@Service
public class MessageProcessor {

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    private IceGathering ice;

    private final Map<MessageType, Integer> messageCountTracker = new HashMap<>();

    public void processMessageAsServer(String sessionId, String userName, StompMessage payload) {
        log.info("""
                        Received message...
                        "sessionId": {}
                        "user": {}
                        "payload": {}
                        """,
                sessionId, userName, payload.toString());

        switch (payload.getType()) {
            case TEST ->
                    sendMessageToPeer(userName, String.format("%s message received at %s", payload.getType(), new Date()));
        }
    }

    public void processMessageAsClient(DodoClientController client, String sessionId, StompHeaders headers, StompMessage payload) {
        log.info("""
                        Received message...
                        \n
                        "sessionId": {}
                        "user": {},
                        "stompHeaders": {},
                        "payload": {}
                        """,
                sessionId, payload.getFrom(), headers, payload);

        switch (payload.getType()) {
            case PEERS -> processPeers(client, payload);
            case ONLINE, OFFLINE, DENIED -> handleInitiate(client, payload);
            case ICE_REQUEST, ICE_OFFER -> handleIce(client, payload);
        }
    }

    private void processPeers(DodoClientController client, StompMessage payload) {
        messageCountTracker.put(
                payload.getType(),
                messageCountTracker.getOrDefault(
                        payload.getType(),
                        0
                ) + 1
        );

        PeersDto peers = null;
        try {
            peers = new ObjectMapper().readValue(
                    payload.getMessage(),
                    PeersDto.class
            );
        } catch (JsonProcessingException exception) {
            log.error("An error occurred while casting payload to <PeersDto.class>", exception);
        }
        assert peers != null;

        if (peers.getDodoAddresses().isEmpty()) {
            if (messageCountTracker.get(MessageType.PEERS) == 3) {
                log.info("Attempted to retrieve PEERS from Signaling Server but found none; Ending WebRTC init");
                return;
            }
            try {
                TimeUnit.SECONDS.sleep(10);
            } catch (InterruptedException exception) {
                log.error("Unexpected error occurred when introducing delay", exception);
            }
            WebrtcService.requestPeersFromServer(client, messageCountTracker.get(MessageType.PEERS) + 1);
        } else
            for (String address : peers.getDodoAddresses())
                sendINITIATE(client, address);
    }

    private void sendINITIATE(DodoClientController client, String address) {
        StompMessage message = new StompMessage();
        message.setFrom(client.getDodoAddress());
        message.setType(MessageType.INITIATE);
        try {
            message.setMessage(
                    new ObjectMapper()
                            .writerWithDefaultPrettyPrinter()
                            .writeValueAsString(
                                    new InitiateDto(address)
                            )
            );
        } catch (JsonProcessingException exception) {
            log.error("Encountered an error when converting to json", exception);
        }

        client.sendMessage("/signal/message", message);
    }

    private void handleInitiate(DodoClientController client, StompMessage payload) {
        InitiateDto initiateDto = null;
        try {
            initiateDto = new ObjectMapper().readValue(
                    payload.getMessage(),
                    InitiateDto.class
            );
        } catch (JsonProcessingException exception) {
            log.error("An error occurred while casting payload to <InitiateDto.class>", exception);
        }

        switch (payload.getType()) {
            case OFFLINE -> {
                messageCountTracker.put(
                        payload.getType(),
                        messageCountTracker.getOrDefault(
                                payload.getType(),
                                0
                        ) + 1
                );

                try {
                    TimeUnit.SECONDS.sleep(10);
                } catch (InterruptedException exception) {
                    log.error("Unexpected error occurred when introducing delay", exception);
                }

                if (messageCountTracker.get(payload.getType()) == 3) {
                    assert initiateDto != null;
                    log.info("Attempted to establish connection with {} through the Signaling Server multiple times but was found to be OFFLINE.", initiateDto.getInitiateTo());
                    return;
                }

                client.sendMessage(
                        "/signal/message",
                        new StompMessage(
                                client.getDodoAddress(),
                                MessageType.INITIATE,
                                payload.getMessage()
                        )
                );
            }
            case ONLINE -> {
                // request for ice candidates
                try {
                    assert initiateDto != null;
                    client.sendMessage(
                            "/signal/message",
                            new StompMessage(
                                    client.getDodoAddress(),
                                    ICE_REQUEST,
                                    new ObjectMapper()
                                            .writerWithDefaultPrettyPrinter()
                                            .writeValueAsString(
                                                    new IceRequestDto(
                                                            initiateDto.getInitiateTo()
                                                    )
                                            )
                            )
                    );
                } catch (JsonProcessingException exception) {
                    log.error("Encountered an error when converting to json", exception);
                }
            }
            case DENIED -> {
                // do nothing
            }
        }
    }

    private void handleIce(DodoClientController client, StompMessage payload) {
        switch (payload.getType()) {
            case ICE_REQUEST -> {
                try {
                    client.sendMessage(
                            "/signal/message",
                            new StompMessage(
                                    client.getDodoAddress(),
                                    ICE_OFFER,
                                    new ObjectMapper()
                                            .writerWithDefaultPrettyPrinter()
                                            .writeValueAsString(
                                                    new IceOfferDto(
                                                            payload.getFrom(),
                                                            ice.getIceCandidate()
                                                    )
                                            )
                            )
                    );
                } catch (JsonProcessingException exception) {
                    log.error("An error occurred while casting payload to <IceRequestDto.class> or when writing it to String", exception);
                }
            }
            case ICE_OFFER -> {
                IceOfferDto iceOffer = null;
                try {
                    iceOffer = new ObjectMapper().readValue(
                            payload.getMessage(),
                            IceOfferDto.class
                    );
                } catch (JsonProcessingException exception) {
                    log.error("An error occurred while casting payload to <IceOfferDto.class>", exception);
                }

                assert iceOffer != null;
            }
        }
    }

    public void sendMessageToPeer(String sendTo, String message) {
        this.simpMessagingTemplate.convertAndSendToUser(sendTo, "/queue/reply", message);
    }
}
