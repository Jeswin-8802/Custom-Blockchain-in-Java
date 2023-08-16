package io.mycrypto.webrtc.service;

import io.mycrypto.core.repository.KeyValueRepository;
import io.mycrypto.webrtc.dto.StompMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

import static io.mycrypto.core.repository.DbName.*;

@Slf4j
@Service
public class MessageProcessor {

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    @Autowired(required = false)
    private KeyValueRepository<String, String> rocksDB;

    private Map<String, Object> peers = new HashMap<>();

    public void processMessageFromPeer(String sessionId, String userName, StompMessage payload) {
        log.info("""
                \n
                "sessionId": {},
                "user": {},
                "payload": {}
                """,
                sessionId, userName, payload.toString());
        sendMessageToPeer(userName, "received");
    }

    public void sendMessageToPeer(String sendTo, String message) {
        this.simpMessagingTemplate.convertAndSendToUser(sendTo, "/queue/reply", message);
    }
}
