package io.mycrypto.entity;

import lombok.Data;

import java.math.BigInteger;

@Data
public class Transaction {
    String transactionId;
    String hash;
    long timeStamp;
    String from; // from Wallet address
    String to; // to Wallet address
    BigInteger satoshis; // The satoshi is currently the smallest unit of the bitcoin currency recorded on the blockchain. It is a one hundred millionth of a single bitcoin (0.00000001 BTC)
    BigInteger UTXO; //  amount of digital currency remaining after a cryptocurrency transaction is executed (Unspent Transaction Output)
}
