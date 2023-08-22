package io.mycrypto.dto;

public enum MessageType {
    PEERS,  // gets available peers to connect to from the Signaling Server
    INITIATE,
    DENIED,
    ONLINE,
    OFFLINE, // if the other Peer is Offline
    /* redirects ICE candidate to the next peer */
    ICE_REQUEST,
    ICE_OFFER
}
