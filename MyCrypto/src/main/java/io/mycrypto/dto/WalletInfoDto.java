package io.mycrypto.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class WalletInfoDto {
    @JsonProperty("public-key")
    String publicKey;

    @JsonProperty("private-key")
    String privateKey;

    @JsonProperty("hash160")
    String hash160;

    @JsonProperty("chicken-coin-address")
    String address;

    @JsonProperty("balance-in-chicken-coin")
    BigDecimal balance;
}
