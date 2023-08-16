package io.mycrypto.webrtc.dto;

import de.javawi.jstun.util.Address;
import lombok.Data;

@Data
public class IceCandidate {
    private String server;
    private Address address;
    private int port;
}
