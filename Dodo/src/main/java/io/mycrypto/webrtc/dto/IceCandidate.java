package io.mycrypto.webrtc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.javawi.jstun.util.Address;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class IceCandidate {
    @JsonProperty("server")
    private String server;
    @JsonProperty("address")
    private String address;
    @JsonProperty("port")
    private int port;

    @Override
    public String toString() {
        return String.format("""
                Server: %s,
                Address: %s,
                Port: %s
                """, server, address, port);
    }
}
