package io.mycrypto.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Input {
    @JsonProperty("transaction-id")
    String transactionId; // 32 bytes 🔄
    @JsonProperty("vout")
    String vout; // 4 bytes 🔄
    @JsonProperty("script-signature-size")
    String size; // (in hex) (1 byte)
    @JsonProperty("script-signature")
    String scriptSig;
}
