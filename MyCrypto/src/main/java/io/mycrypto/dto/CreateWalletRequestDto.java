package io.mycrypto.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class CreateWalletRequestDto {
    @JsonProperty(value = "name", required = true)
    String walletName;
    @JsonProperty("key-name")
    String keyName;
}
