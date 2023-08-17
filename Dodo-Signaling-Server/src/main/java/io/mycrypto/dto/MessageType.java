package io.mycrypto.dto;

public enum MessageType {
    PEERS,  // gets available peers to connect to from the Signaling Server
    INITIATE,
    DENIED,
    OFFLINE, // if the other Peer is Offline
    ICE,    // redirects ICE candidate to the next peer
}
