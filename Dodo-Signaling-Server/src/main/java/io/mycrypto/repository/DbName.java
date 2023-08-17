package io.mycrypto.repository;

public enum DbName {
    PEER_STATUS, // "dodo-address": ONLINE/OFFLINE
    ICE_CANDIDATES, // "dodo-address": <ICE>
    PEER_ADDRESSES /*
        "ip-address": [
            "dodo-address1",
            "dodo-address2",
            .
            .
            .
        ]
    */
}
