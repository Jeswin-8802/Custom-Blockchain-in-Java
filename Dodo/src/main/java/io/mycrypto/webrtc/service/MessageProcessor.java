package io.mycrypto.webrtc.service;

import io.mycrypto.webrtc.dto.StompMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MessageProcessor {
    public void processMessageFromPeer(String sessionId, String userName, StompMessage payload) {
        log.info("""
                    "sessionId": {},
                    "user": {},
                    "payload": {}\s
                """,
                sessionId, userName, payload);
    }
}
