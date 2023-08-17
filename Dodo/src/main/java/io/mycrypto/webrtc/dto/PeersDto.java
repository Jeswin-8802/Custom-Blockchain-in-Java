package io.mycrypto.webrtc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class PeersDto {
    @JsonProperty("online-peers")
    List<String> dodoAddresses;

    public PeersDto() {
        // empty constructor needed to deserialize
    }

    public PeersDto(List<String> dodoAddresses) {
        this.dodoAddresses = dodoAddresses;
    }
}
