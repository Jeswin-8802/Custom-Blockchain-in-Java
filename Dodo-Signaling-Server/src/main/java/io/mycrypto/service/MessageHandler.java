package io.mycrypto.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mycrypto.dto.*;
import io.mycrypto.repository.DbName;
import io.mycrypto.repository.KeyValueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class MessageHandler {

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    private KeyValueRepository<String, String> rocksDB;

    private final String destionationUri = "/queue/reply";

    private final String serverIdentity = "dodo-ss";

    public void processMessageFromPeer(String sessionId, String userName, StompMessage payload) {
        log.info("""
                        Message Received,
                            "sessionId": {},
                            "user": {},
                            "payload": {}
                        """,
                sessionId, userName, payload.toString());

        recordAndSetPeerStatus(userName, PeerStatus.ONLINE);

        switch (payload.getType()) {
            case PEERS -> getPeersAndSendToClient(userName);
            case INITIATE -> handleInitiate(userName, payload);
        }
    }

    private void recordAndSetPeerStatus(String dodoAddress, PeerStatus status) {
        rocksDB.save(
                dodoAddress,
                status.toString(),
                DbName.PEER_STATUS
        );
    }

    private void getPeersAndSendToClient(String peer) {
        List<String> dodoAddresses = rocksDB.getList(DbName.PEER_STATUS)
                .entrySet().stream()
                .filter(x ->
                        !x.getKey().equals(peer) &&
                                x.getValue().equals(PeerStatus.ONLINE.toString())
                )
                .map(Map.Entry::getKey)
                .toList();

        StompMessage message = null;
        try {
            message = new StompMessage(
                    serverIdentity,
                    MessageType.PEERS,
                    new ObjectMapper()
                            .writerWithDefaultPrettyPrinter()
                            .writeValueAsString(
                                    new PeersDto(dodoAddresses)
                            )
            );
        } catch (JsonProcessingException exception) {
            log.error("Encountered an error when converting to json", exception);
        }

        log.info("Sending message \n{} to peer {}", message, peer);

        assert message != null;
        this.simpMessagingTemplate.convertAndSendToUser(
                peer,
                destionationUri,
                message
        );
    }

    private void handleInitiate(String peer, StompMessage payload) {
        InitiateDto initiate = null;
        try {
            initiate = new ObjectMapper().readValue(
                    payload.getMessage(),
                    InitiateDto.class
            );
        } catch (JsonProcessingException exception) {
            log.error("An error occurred while casting payload to <InitiateDto.class>", exception);
        }

        assert initiate != null;
        log.info(" ------------------- {} : {}", initiate.getClient(), initiate.getInitiateTo());
    }
}
