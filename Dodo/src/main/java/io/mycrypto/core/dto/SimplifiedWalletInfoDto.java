package io.mycrypto.core.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SimplifiedWalletInfoDto {
    @JsonProperty("name")
    String walletName;

    @JsonProperty("dodo-coin-address")
    String address;

    @JsonProperty("balance-in-dodo-coin")
    BigDecimal balance;
}
