package io.mycrypto.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MakeTransactionDto {
    @JsonProperty("from-wallet-address")
    String from;

    @JsonProperty(value = "to-wallet-address", required = true)
    String to;

    @JsonProperty(value = "amount", required = true)
    BigDecimal amount;

    @JsonProperty("transaction-fee")
    BigDecimal transactionFee;

    @JsonProperty("utxo-selection-algorithm")
    Integer algorithm;

    @JsonProperty("message")
    String message;
}
