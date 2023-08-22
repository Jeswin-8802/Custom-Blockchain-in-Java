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

    private final String serverIdentity = "dodo-ss";

    public void processMessageFromPeer(String sessionId, String userName, StompMessage payload) {
        log.info("""
                        Message Received,
                            "sessionId": {},
                            "user": {},
                            "payload": {}
                        """,
                sessionId, userName, payload.toString());

        if (rocksDB.find(userName, DbName.PEER_STATUS) == null)
            recordAndSetPeerStatus(userName, PeerStatus.ONLINE.toString());

        switch (payload.getType()) {
            case PEERS -> getPeersAndSendToClient(userName);
            case INITIATE -> handleInitiate(userName, payload);
            case ICE_REQUEST, ICE_OFFER -> handleIce(payload);
        }
    }

    private void recordAndSetPeerStatus(String dodoAddress, String status) {
        rocksDB.save(
                dodoAddress,
                status,
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

        assert message != null;
        sendMessage(
                peer,
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

        String peerStatus = rocksDB.find(
                peer,
                DbName.PEER_STATUS
        );
        log.info("Status of peer {} : \n{}", peer, peerStatus);

        assert initiate != null;
        String initiateToStatus = rocksDB.find(
                initiate.getInitiateTo(),
                DbName.PEER_STATUS
        );
        log.info("Status of peer to initiate connection to {} : \n{}", initiate.getInitiateTo(), initiateToStatus);

        if (initiateToStatus.equals(PeerStatus.OFFLINE.toString())) {
            log.info("The peer to initiate connection to is OFFLINE.");
            sendMessage(
                    peer,
                    new StompMessage(
                            serverIdentity,
                            MessageType.OFFLINE,
                            payload.getMessage()
                    )
            );
        } else if ( // if the peer in session has a status of either ONLINE or OFFLINE
                List.of(
                        PeerStatus.ONLINE.toString(),
                        PeerStatus.OFFLINE.toString()
                ).contains(
                        peerStatus
                )
        ) {
            try {
                PeersDto peersDto;
                if (    // if the peer to initiate connection to does not have a status of either ONLINE or OFFLINE
                        !List.of(
                                PeerStatus.ONLINE.toString(),
                                PeerStatus.OFFLINE.toString()
                        ).contains(
                                initiateToStatus
                        )
                ) {
                    peersDto = new ObjectMapper()
                            .readValue(
                                    rocksDB.find(
                                            initiate.getInitiateTo(),
                                            DbName.PEER_STATUS
                                    ),
                                    PeersDto.class
                            );
                    peersDto.getDodoAddresses().add(peer);
                } else {
                    peersDto = new PeersDto(List.of(peer));
                }

                // Setting Value to peer address instead of Status to track which peer initiated connection first
                recordAndSetPeerStatus(
                        initiate.getInitiateTo(),
                        new ObjectMapper()
                                .writerWithDefaultPrettyPrinter()
                                .writeValueAsString(peersDto)
                );
            } catch (JsonProcessingException exception) {
                log.error("Encountered an error when converting to json and back", exception);
            }

            log.info("Peer {} is ONLINE and {} can INITIATE connection to it.", initiate.getInitiateTo(), peer);
            sendMessage(
                    peer,
                    new StompMessage(
                            serverIdentity,
                            MessageType.ONLINE,
                            payload.getMessage()
                    )
            );
        } else {
            log.info("Peer {} had already initiated connection with {} therefore the request has been DENIED.", initiate.getInitiateTo(), peer);
            sendMessage(
                    peer,
                    new StompMessage(
                            serverIdentity,
                            MessageType.DENIED,
                            payload.getMessage()
                    )
            );
        }
    }

    private void handleIce(StompMessage payload) {
        IceRequestDto iceRequest = null;
        IceOfferDto iceOffer = null;
        try {
            if (payload.getType().equals(MessageType.ICE_REQUEST))
                iceRequest = new ObjectMapper().readValue(
                        payload.getMessage(),
                        IceRequestDto.class
                );
            else
                iceOffer = new ObjectMapper().readValue(
                        payload.getMessage(),
                        IceOfferDto.class
                );
        } catch (JsonProcessingException exception) {
            log.error("An error occurred while casting payload to <IceDto.class>", exception);
        }
        sendMessage(
                payload.getType().equals(MessageType.ICE_REQUEST) ? iceRequest.getTo() : iceOffer.getTo(),
                payload
        );
    }

    private void sendMessage(String peer, StompMessage message) {
        log.info("Sending message \n{} to peer {}", message, peer);
        String destinationUri = "/queue/reply";
        this.simpMessagingTemplate.convertAndSendToUser(
                peer,
                destinationUri,
                message
        );
    }
}
