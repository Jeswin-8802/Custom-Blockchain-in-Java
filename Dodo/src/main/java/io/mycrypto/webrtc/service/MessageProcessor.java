package io.mycrypto.webrtc.service;

import io.mycrypto.core.repository.KeyValueRepository;
import io.mycrypto.webrtc.dto.StompMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class MessageProcessor {

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    private KeyValueRepository<String, String> rocksDB;

    private Map<String, Object> peers = new HashMap<>();

    public void processMessageFromPeerAsServer(String sessionId, String userName, StompMessage payload) {
        log.info("""
                Received message...
                \n
                "sessionId": {}
                "user": {},
                "payload": {}
                """,
                sessionId, userName, payload.toString());
        sendMessageToPeer(userName, String.format("%s message received at %s", payload.getType(), new Date()));
    }

    public void processMessageAsClient(String sessionId, StompHeaders headers, StompMessage payload) {
        log.info("""
                Received message...
                \n
                "sessionId": {}
                "user": {},
                "payload": {}
                """,
                sessionId, payload.getFrom(), payload.toString());
    }

    public void sendMessageToPeer(String sendTo, String message) {
        this.simpMessagingTemplate.convertAndSendToUser(sendTo, "/queue/reply", message);
    }
}
