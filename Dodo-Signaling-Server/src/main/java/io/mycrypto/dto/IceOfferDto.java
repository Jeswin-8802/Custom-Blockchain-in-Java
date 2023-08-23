package io.mycrypto.dto;

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
}
