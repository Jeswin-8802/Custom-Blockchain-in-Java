package io.mycrypto.webrtc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class IceOfferDto {
    @JsonProperty("from-dodo-address")
    private String from;
    @JsonProperty("to-dodo-address")
    private String to;
    @JsonProperty("ice-candidate")
    private IceCandidate ice;

    public IceOfferDto() {
        // empty constructor needed to deserialize
    }

    public IceOfferDto(String from, String to, IceCandidate ice) {
        this.from = from;
        this.to = to;
        this.ice = ice;
    }
}
