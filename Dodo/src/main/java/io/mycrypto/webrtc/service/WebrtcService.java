package io.mycrypto.webrtc.service;

import io.mycrypto.core.config.DodoCommonConfig;
import io.mycrypto.core.repository.KeyValueRepository;
import io.mycrypto.core.service.ResponseService;
import io.mycrypto.webrtc.config.WebSocketClientConfig;
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

import static io.mycrypto.core.repository.DbName.*;

@Slf4j
@Service
public class WebrtcService {

    @Autowired
    private DodoCommonConfig config;

    @Autowired
    private WebSocketClientConfig clientConfig;

    @Autowired
    private IceGathering ice;

    @Autowired
    private KeyValueRepository<String, String> rocksDB;

    @EventListener(ApplicationReadyEvent.class)
    public void startPeerStateInstantiation() {
        DodoClientController client = establishConnectionWithSignallingServer();

        if (client == null) {
            log.info("Unable to connect to Signalling Server; WebRTC will be disabled");
            return;
        }

        requestPeersFromServer(client, 1);
    }

    private DodoClientController establishConnectionWithSignallingServer() {
        DodoClientController client = clientConfig.clientForSS();

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
            client.shutDown();
            return null;
        }

        client.setDodoAddress(getDefaultWalletAddress());
        client.connect(
                String.format(
                        "ws://%s:8080/ss",
                        config.getSignallingServerFQDN()
                )
        );
        client.subscribe("/peer/queue/reply");

        return client;
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

    public static void requestPeersFromServer(DodoClientController client, int attempt) {
        log.info("Requesting peers from {}; sending <PEERS> packet; attempt {}", client.getServerURL(), attempt);
        StompMessage message = new StompMessage();
        message.setFrom(client.getDodoAddress());
        message.setType(MessageType.PEERS);

        client.sendMessage("/signal/message", message);
    }

    private String getDefaultWalletAddress() {
        return ResponseService.castToWalletInfoDto(
                rocksDB.find(
                        config.getDefaultWalletName(),
                        WALLETS
                )
        ).getAddress();
    }
}
