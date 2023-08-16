package io.mycrypto.webrtc.service;

import io.mycrypto.core.config.DodoCommonConfig;
import io.mycrypto.webrtc.controller.DodoClientController;
import io.mycrypto.webrtc.dto.MessageType;
import io.mycrypto.webrtc.dto.StompMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class WebrtcService {

    @Autowired
    private DodoCommonConfig config;

    @Autowired
    private IceGathering ice;

    public void startPeerStateInstantiation() {
        establishConnectionWithSignallingServer();
    }

    private void establishConnectionWithSignallingServer() {
        DodoClientController client = new DodoClientController();
//        client.connect(String.format(
//                "ws://%s:8080/dodo-ss",
//                config.getSignallingServerFQDN()
//        ));
//        client.subscribe("/peer/queue/reply");

        StompMessage message = new StompMessage();
        message.setFrom(config.getAdminAddress());
        message.setType(MessageType.PEERS);

        client.sendMessage("/dodo/signal", message);

        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException exception) {
            log.error("An error occurred when introducing a delay", exception);
        }

        // gather ICE candidates
        ice.getIceCandidate();

    }
}
