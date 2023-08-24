package io.mycrypto.webrtc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import io.mycrypto.core.repository.DbName;
import io.mycrypto.core.repository.KeyValueRepository;
import io.mycrypto.webrtc.controller.DodoClientController;
import io.mycrypto.webrtc.dto.IceOfferDto;
import io.mycrypto.webrtc.dto.IceRequestDto;
import io.mycrypto.webrtc.dto.InitiateDto;
import io.mycrypto.webrtc.dto.PeersDto;
import io.mycrypto.webrtc.entity.StompMessage;
import io.mycrypto.webrtc.service.tags.MessageType;
import io.mycrypto.webrtc.service.tags.P2pStatus;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.mycrypto.webrtc.service.tags.MessageType.ICE_OFFER;
import static io.mycrypto.webrtc.service.tags.MessageType.ICE_REQUEST;

@Slf4j
@Service
public class MessageProcessor {

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    private IceGathering ice;

    @Autowired
    private KeyValueRepository<String, String> rocksDb;

    private final Map<MessageType, Integer> messageCountTracker = new HashMap<>();

    private final String signalingServerDestinationUri = "/signal/message";

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
        if (
                headers.containsKey("message") &&
                        headers.get("message").get(0).equals("Session closed.")
        ) {
            log.info("Session Closed...");
            return;
        }

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
            case FINISH -> handleFinish(client, payload);
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
        rocksDb.save(
                DbName.PEERS.toString(),
                peers.getDodoAddresses().toString().replaceAll("[\\[\\]\\s]", ""),
                DbName.WEBRTC
        );

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

        client.sendMessage(signalingServerDestinationUri, message);
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
                        signalingServerDestinationUri,
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
                            signalingServerDestinationUri,
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
                            signalingServerDestinationUri,
                            new StompMessage(
                                    client.getDodoAddress(),
                                    ICE_OFFER,
                                    new ObjectMapper()
                                            .writerWithDefaultPrettyPrinter()
                                            .writeValueAsString(
                                                    new IceOfferDto(
                                                            client.getDodoAddress(),
                                                            payload.getFrom(),  // this condition is arrived at when the ICE_REQUEST payload follows : peer1(remote peer) -> signaling-server -> peer2(current-peer); Here peer2 is processing the request
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
                // save to P2P DB
                rocksDb.save(
                        iceOffer.getFrom(),
                        P2pStatus.DISCONNECTED.toString(),
                        DbName.P2P
                );

                String resultFromDb = rocksDb.find(
                        DbName.ICE.toString(),
                        DbName.WEBRTC
                );

                JSONObject saveToDb = null;
                if (resultFromDb == null) {
                    saveToDb = new JSONObject();
                } else {
                    try {
                        saveToDb = (JSONObject) new JSONParser().parse(resultFromDb);
                    } catch (ParseException exception) {
                        log.error("An exception occurred when parsing a string to JSON object", exception);
                    }
                }

                assert saveToDb != null;
                saveToDb.put(
                        iceOffer.getFrom(),
                        iceOffer.getIce().toJsonObject()
                );

                rocksDb.save(
                        DbName.ICE.toString(),
                        saveToDb.toJSONString(),
                        DbName.WEBRTC
                );
                
                /* check for if this peer can close session with the signalling server 
                by checking if there are any peers left to provide their ice candidates */
                Set<String> remotePeers = Arrays.stream(rocksDb.find(
                        DbName.PEERS.toString(),
                        DbName.WEBRTC
                ).split(",")).collect(Collectors.toSet());

                if (remotePeers.size() == 1 && remotePeers.contains(iceOffer.getFrom())) {
                    client.sendMessage(
                            signalingServerDestinationUri,
                            new StompMessage(
                                    client.getDodoAddress(),
                                    MessageType.FINISH,
                                    String.format(
                                            """
                                                    {
                                                        "to": "%s"
                                                    }
                                                    """,
                                            iceOffer.getFrom()
                                    )
                            )
                    );
                }

                closeSessionWithSignallingServer(client, payload);
            }
        }
    }

    private void handleFinish(DodoClientController client, StompMessage payload) {
        log.info("""
                \n
                ------------------------------------------------
                 ICE exchange between PEERS has been completed.
                   Disconnecting from Dodo Signalling Server.
                     Attempting to connect to remote PEERS.
                -------------------------------------------------
                """);
        closeSessionWithSignallingServer(client, payload);
    }

    private void closeSessionWithSignallingServer(DodoClientController client, StompMessage payload) {
        if (
                !Strings.isNullOrEmpty(rocksDb.find(
                        DbName.PEERS.toString(),
                        DbName.WEBRTC
                ))
        ) {
            List<String> peers =
                    new ArrayList<>(Arrays.stream(
                            rocksDb.find(
                                    DbName.PEERS.toString(),
                                    DbName.WEBRTC
                            ).split(",")
                    ).toList());

            if (peers.size() == 1) {
                client.shutDown();
            }

            peers.remove(payload.getFrom());
            rocksDb.save(
                    DbName.PEERS.toString(),
                    peers.isEmpty() ?
                            "" :
                            peers.toString().replaceAll("[\\[\\]\\s]", ""),
                    DbName.WEBRTC
            );
        }
    }

    public void sendMessageToPeer(String sendTo, String message) {
        this.simpMessagingTemplate.convertAndSendToUser(sendTo, "/queue/reply", message);
    }
}
