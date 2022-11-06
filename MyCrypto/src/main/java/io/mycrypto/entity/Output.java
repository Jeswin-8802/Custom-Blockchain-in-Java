package io.mycrypto.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;

@Data
@ToString
public class Output {
    @JsonProperty("amount")
    BigDecimal amount; // amount to transfer
    // FACT(Bitcoin): The satoshi is currently the smallest unit of the bitcoin currency recorded on the blockchain. It is a one hundred millionth of a single bitcoin (0.00000001 BTC)
    @JsonProperty("n")
    long n;
    @JsonProperty("script-public-key")
    ScriptPublicKey scriptPubKey;
}
