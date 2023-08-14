package io.mycrypto.webrtc.service;

import io.mycrypto.core.config.DodoCommonConfig;
import io.mycrypto.webrtc.dto.StompMessage;
import io.mycrypto.webrtc.controller.DodoClientController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class WebrtcService {

    @Autowired
    private DodoCommonConfig config;

    @Autowired
    private IceGathering ice;

    private Map<String, Object> availablePeers = new HashMap<>();

    public void startPeerStateInstantiation() {
        establishConnectionWithSignallingServer();
    }

    private void establishConnectionWithSignallingServer() {
        DodoClientController client = new DodoClientController();
//        client.connect(String.format(
//                "ws://%s:8080/dodo-p2p",
//                config.getSignallingServerFQDN()
//        ));
//        client.subscribe("/peer/queue/reply");

        // gather ICE candidates
        ice.getIceCandidate();

        StompMessage message = new StompMessage();
        message.setId(String.valueOf(UUID.randomUUID()));
        message.setType("ICE");

    }
}
