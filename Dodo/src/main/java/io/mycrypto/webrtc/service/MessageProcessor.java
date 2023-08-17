package io.mycrypto.webrtc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mycrypto.core.repository.KeyValueRepository;
import io.mycrypto.webrtc.controller.DodoClientController;
import io.mycrypto.webrtc.dto.InitiateDto;
import io.mycrypto.webrtc.dto.MessageType;
import io.mycrypto.webrtc.dto.PeersDto;
import io.mycrypto.webrtc.dto.StompMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MessageProcessor {

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    private KeyValueRepository<String, String> rocksDB;

    private Map<MessageType, Integer> messageCountTracker = new HashMap<>();

    public void processMessageAsServer(String sessionId, String userName, StompMessage payload) {
        log.info("""
                        Received message...
                        "sessionId": {}
                        "user": {}
                        "payload": {}
                        """,
                sessionId, userName, payload.toString());

        switch (payload.getType()) {
            case TEST -> sendMessageToPeer(userName, String.format("%s message received at %s", payload.getType(), new Date()));
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
            for (String address: peers.getDodoAddresses())
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
                                    new InitiateDto(client.getDodoAddress(), address)
                            )
            );
        } catch (JsonProcessingException exception) {
            log.error("Encountered an error when converting to json", exception);
        }

        client.sendMessage("/signal/message", message);
    }

    public void sendMessageToPeer(String sendTo, String message) {
        this.simpMessagingTemplate.convertAndSendToUser(sendTo, "/queue/reply", message);
    }
}
