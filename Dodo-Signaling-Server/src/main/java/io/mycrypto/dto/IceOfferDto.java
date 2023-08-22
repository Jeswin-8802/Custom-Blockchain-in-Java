package io.mycrypto.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class IceOfferDto {
    @JsonProperty("to-dodo-address")
    private String to;
    @JsonProperty("ice-candidate")
    private IceCandidate ice;

    public IceOfferDto() {
        // empty constructor needed to deserialize
    }

    public IceOfferDto(String to, IceCandidate ice) {
        this.to = to;
        this.ice = ice;
    }
}
