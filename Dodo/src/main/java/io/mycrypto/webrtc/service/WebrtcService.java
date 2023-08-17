package io.mycrypto.webrtc.service;

import io.mycrypto.core.config.DodoCommonConfig;
import io.mycrypto.webrtc.controller.DodoClientController;
import io.mycrypto.webrtc.dto.MessageType;
import io.mycrypto.webrtc.dto.StompMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class WebrtcService {

    @Autowired
    private DodoCommonConfig config;

    @Autowired
    private IceGathering ice;

    @EventListener(ApplicationReadyEvent.class)
    public void startPeerStateInstantiation() {
        establishConnectionWithSignallingServer();
    }

    private void establishConnectionWithSignallingServer() {
        DodoClientController client = new DodoClientController();

        int count = 0;
        while (count <= 2 && !pingHost(config.getSignallingServerFQDN(), 8080, 1000)) {
            try {
                TimeUnit.SECONDS.sleep(9);
            } catch (InterruptedException exception) {
                log.error("Unexpected error occurred when introducing delay", exception);
            }
            count++;
        }

        if (count == 3) {
            log.error("Unable to connect to Server after 3 attempts in 30 seconds");
            return;
        }

        client.connect(
                String.format(
                        "ws://%s:8080/ss",
                        config.getSignallingServerFQDN()
                )
        );
        client.subscribe("/peer/queue/reply");

        StompMessage message = new StompMessage();
        message.setFrom(config.getAdminAddress());
        message.setType(MessageType.PEERS);

        client.sendMessage("/signal/message", message);

        // gather ICE candidates
        ice.getIceCandidate();

    }

    private boolean pingHost(String host, int port, int timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeout);
            return true;
        } catch (IOException exception) {
            log.error("Unable to reach {} at port {}, trying again in 10 seconds", host, port);
            return false; // Either timeout or unreachable or failed DNS lookup.
        }
    }
}
