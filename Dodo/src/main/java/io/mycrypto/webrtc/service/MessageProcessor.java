package io.mycrypto.webrtc.service;

import io.mycrypto.core.repository.KeyValueRepository;
import io.mycrypto.webrtc.dto.MessageType;
import io.mycrypto.webrtc.dto.StompMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

import static io.mycrypto.core.repository.DbName.*;

@Slf4j
@Service
public class MessageProcessor {

    @Autowired
    private KeyValueRepository<String, String> rocksDB;

    private Map<String, Object> peers = new HashMap<>();

    public MessageProcessor() {
        rocksDB.find(MessageType.PEERS.toString(), WEBRTC);
    }

    public void processMessageFromPeer(String sessionId, String userName, StompMessage payload) {
        log.info("""
                    "sessionId": {},
                    "user": {},
                    "payload": {}\s
                """,
                sessionId, userName, payload);
    }
}
