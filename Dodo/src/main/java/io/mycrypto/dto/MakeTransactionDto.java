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

    /*** for the below 2 fields, a single number denotes the number of parts the output must be divided into
     * whereas when given as a ratio, it will divide those parts evenly into the ratios defined
     * for Example:
     * when given as "3", the output gets evenly divided into 3 outputs of equal amounts
     * when given as "1:2:3", the output gets divided into 3 outputs with its amounts in the ratio specified
    ***/
    @JsonProperty(value = "to-output-parts")
    String toOutputParts;
    @JsonProperty(value = "receiving-output-parts")
    String receivingOutputParts; // is overriden by the value given in config.properties unless explicitly specified

    @JsonProperty("message")
    String message;
}
