package io.mycrypto.service;

import io.mycrypto.dto.PeerStatus;
import io.mycrypto.repository.DbName;
import io.mycrypto.repository.KeyValueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ConnectionCheckScheduler {

    @Autowired
    private KeyValueRepository<String, String> rocksDB;

    private final SimpUserRegistry simpUserRegistry;

    public ConnectionCheckScheduler(SimpUserRegistry simpUserRegistry) {
        this.simpUserRegistry = simpUserRegistry;
    }

    @Scheduled(fixedDelay = 120000)
    public void connectionHealthCheck() {
        if (simpUserRegistry.getUsers().isEmpty()) {
            log.info("No peers connected");
        } else {
            log.info("Peers: {}", simpUserRegistry.getUsers());

            List<String> peers = simpUserRegistry.getUsers().stream().map(SimpUser::getName).toList();

            for (Map.Entry<String, String> entry : rocksDB.getList(DbName.PEER_STATUS).entrySet()) {
                if (!peers.contains(entry.getKey())
                        && entry.getValue().equals(PeerStatus.ONLINE.toString()))
                    rocksDB.save(
                            entry.getKey(),
                            PeerStatus.OFFLINE.toString(),
                            DbName.PEER_STATUS
                    );
            }
        }
    }
}
