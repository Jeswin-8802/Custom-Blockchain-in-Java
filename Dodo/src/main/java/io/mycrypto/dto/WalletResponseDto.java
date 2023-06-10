package io.mycrypto.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;

@Data
@ToString
public class WalletResponseDto {
    @JsonProperty("public-key")
    String publicKey;

    @JsonProperty("private-key")
    String privateKey;

    @JsonProperty("hash160")
    String hash160;

    @JsonProperty("balance-in-dodo-coin")
    BigDecimal balance;

    @JsonProperty("dodo-coin-address")
    String address;
}
