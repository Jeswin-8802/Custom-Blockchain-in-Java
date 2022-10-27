package io.mycrypto.entity;

public class Transaction {
    public String transactionId;
    public String hash;
    private long timeStamp;
    private String from; // from Wallet address
    private String to; // to Wallet address
    private double amount;
}
