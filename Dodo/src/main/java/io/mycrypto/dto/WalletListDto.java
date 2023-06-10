package io.mycrypto.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WalletListDto {
    @JsonProperty("wallets")
    List<SimplifiedWalletInfoDto> wallets;
}
