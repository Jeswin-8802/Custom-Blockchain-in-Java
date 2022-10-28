package io.mycrypto.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;

import java.math.BigInteger;

@Data
@ToString
public class WalletResponseDto {
    @JsonProperty("private-key")
    String privateKey;

    @JsonProperty("public-key")
    String publicKey;

    @JsonProperty("utxo")
    BigInteger UTXO;
}
