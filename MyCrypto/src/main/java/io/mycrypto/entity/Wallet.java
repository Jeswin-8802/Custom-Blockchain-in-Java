package io.mycrypto.entity;

import lombok.Data;

import java.math.BigInteger;

@Data
public class Wallet {
    String publicKey;
    String privateKey;
    BigInteger UTXO; //  amount of digital currency remaining after a cryptocurrency transaction is executed (Unspent Transaction Output)
}
