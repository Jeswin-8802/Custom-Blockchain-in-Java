package io.mycrypto.webrtc.dto;

public enum MessageType {

    /* Peer 2 Server */

    PEERS,  // gets available peers to connect to from the Signaling Server
    INITIATE,   /*
    initiates connection with another peer.
    Signaling Server locks this request
    and only allows for this peer to act as the client
    and the peer being connected to, the server
    Not the other way round unless, the other peer sends an INITIATE first
    */
    DENIED, // after INITIATE, if the Signaling Server finds that the other Peer initiated first
    ONLINE, // // if the other Peer is Online
    OFFLINE, // if the other Peer is Offline
    ICE_REQUEST,    // requests ICE candidates from the other peer
    ICE_OFFER,    // Sends the ICE candidate to peer through the Signalling Server

    /* P2P */

    CHECK,  // After establishing connection with peer, sends a CHECK message
    RECEIVED, // In response to CHECK
    BLOCK,
    TRANSACTION,

    /* External */
    TEST // Testing from a browser, the message simply gets sent back
}
