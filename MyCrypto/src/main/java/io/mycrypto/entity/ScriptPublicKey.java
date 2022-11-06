package io.mycrypto.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mycrypto.util.Utility;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@ToString
public class ScriptPublicKey {
    @JsonProperty("asm")
    String assembly;
    @JsonProperty("hex")
    String hex;
    @JsonProperty("to-address")
    String address;
    @JsonProperty("type")
    String type = "P2PKH"; // Pay to Public Key Hash (only supported type for the time being)
    @JsonIgnore
    private static final String SCRIPT = "OP_DUP OP_HASH160 %s OP_EQUALVERIFY OP_CHECKSIG";

    public ScriptPublicKey(String hash160, String address) {
        this.assembly = String.format(SCRIPT, hash160);
        this.hex = Utility.bytesToHex(this.assembly.getBytes());
        this.address = address;
    }
}
