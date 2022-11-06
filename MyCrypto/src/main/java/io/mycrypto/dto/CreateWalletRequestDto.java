package io.mycrypto.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class CreateWalletRequestDto {
    @JsonProperty("name")
    String walletName;
}
