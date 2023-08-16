package io.mycrypto.service;

import io.mycrypto.dto.StompMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MessageHandler {

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    public void processMessageFromPeer(String sessionId, String userName, StompMessage payload) {
        log.info("""
                Message Received,
                    "sessionId": {},
                    "user": {},
                    "payload": {}\s
                """,
                sessionId, userName, payload);

    }
}
