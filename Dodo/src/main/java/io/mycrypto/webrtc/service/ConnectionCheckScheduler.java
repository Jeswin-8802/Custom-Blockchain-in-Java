package io.mycrypto.webrtc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ConnectionCheckScheduler {

    @Autowired
    private WebrtcService msgProcessor;

    private final SimpUserRegistry simpUserRegistry;

    public ConnectionCheckScheduler(SimpUserRegistry simpUserRegistry) {
        this.simpUserRegistry = simpUserRegistry;
    }

    @Scheduled(fixedDelay = 120000)
    public void connectionHealthCheck() throws InterruptedException {
        if (simpUserRegistry.getUsers().isEmpty()) {
            log.info("Connected Number of peers/server: {}", simpUserRegistry.getUsers().size());
        } else
            log.info("Users: {}", simpUserRegistry.getUsers());
    }
}
