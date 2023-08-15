package io.mycrypto.core.repository;

public enum DbName {
    BLOCKCHAIN, // Block-Hash ==> block path
    TRANSACTIONS, // Transaction-Hash ==> Transaction Data (as JSON)
    TRANSACTIONS_POOL, // Transaction-Hash ==> Transaction Data (as JSON)
    NODES, // Wallet Address ==> ("IP Address" if foreign | "Wallet Name" if owned)
    WALLETS, // Wallet-Name ==> "PubKey PrvKey hash-160 dodo-coin-address"
    ACCOUNTS, // (as JSON) 👇
    /* Wallet Address ==>
    {
        TransactionId1: "VOUT_1,VOUT_2",
        TransactionId2: "VOUT_1",
        .
        .
        .
    }
     */
    WEBRTC; // used to store information received from peer or server
}
