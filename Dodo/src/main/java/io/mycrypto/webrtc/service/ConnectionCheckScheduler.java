package io.mycrypto.webrtc.service;

import io.mycrypto.core.repository.DbName;
import io.mycrypto.core.repository.KeyValueRepository;
import io.mycrypto.webrtc.service.tags.P2pStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class ConnectionCheckScheduler {

    @Autowired
    private WebrtcService service;

    @Autowired
    private KeyValueRepository<String, String> rocksDb;

    private final SimpUserRegistry simpUserRegistry;

    public ConnectionCheckScheduler(SimpUserRegistry simpUserRegistry) {
        this.simpUserRegistry = simpUserRegistry;
    }

    @Scheduled(fixedDelay = 120000)
    public void connectionHealthCheck() {
        if (simpUserRegistry.getUsers().isEmpty()) {
            log.info("Number of connected peers: {}", simpUserRegistry.getUsers().size());
        } else
            log.info("Peers: {}", simpUserRegistry.getUsers());

        Map<String, String> p2pDbData = rocksDb.getList(DbName.P2P);
        if (!p2pDbData.isEmpty()) {
            service.establishConnectionWithRemotePeersAsClient(
                    p2pDbData
                            .entrySet()
                            .stream()
                            .filter(
                                    x -> x.getValue()
                                            .equals(
                                                    P2pStatus.DISCONNECTED.toString()
                                            )
                            )
                            .map(Map.Entry::getKey)
                            .toList()
            );
        }
    }
}
