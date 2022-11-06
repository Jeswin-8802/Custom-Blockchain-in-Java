package io.mycrypto.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class WalletUTXOsDto {
    @JsonProperty("transaction-id")
    String transactionId;
    @JsonProperty("vout")
    long vout;
    @JsonProperty("amount")
    BigDecimal amount;
}
