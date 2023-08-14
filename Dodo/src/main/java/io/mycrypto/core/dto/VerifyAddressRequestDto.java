package io.mycrypto.core.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class VerifyAddressRequestDto {
    @JsonProperty("dodo-coin-address")
    String address;
    @JsonProperty("hash160-of-public-key")
    String hash160;
}
