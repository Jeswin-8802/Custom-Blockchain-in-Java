package io.mycrypto.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class VerifyAddressRequestDto {
    @JsonProperty("bitcoin-address")
    String address;
    @JsonProperty("public-key")
    String publicKey;
}
