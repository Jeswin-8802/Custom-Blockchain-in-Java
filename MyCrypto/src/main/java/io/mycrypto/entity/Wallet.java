package io.mycrypto.entity;

import lombok.Data;

@Data
public class Wallet {
    String publicKey;
    String privateKey;
}
