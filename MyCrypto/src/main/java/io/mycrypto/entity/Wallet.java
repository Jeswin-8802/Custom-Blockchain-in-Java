package io.mycrypto.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Wallet {
    String publicKey;
    String privateKey;
    BigDecimal UTXO; //  amount of digital currency remaining after a cryptocurrency transaction is executed (Unspent Transaction Output)
}
