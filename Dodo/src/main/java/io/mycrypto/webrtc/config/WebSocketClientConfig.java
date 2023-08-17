package io.mycrypto.webrtc.config;

import io.mycrypto.webrtc.controller.DodoClientController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class WebSocketClientConfig {
    @Bean
    public DodoClientController clientForSS() {
        return new DodoClientController();
    }

    @Bean
    public List<DodoClientController> clientForP2P(List<String> dodoAddresses) {
        List<DodoClientController> clients = new ArrayList<>();
        for (String address: dodoAddresses) {
            DodoClientController client = new DodoClientController();
            client.setDodoAddress(address);
            clients.add(client);
        }
        return clients;
    }
}
