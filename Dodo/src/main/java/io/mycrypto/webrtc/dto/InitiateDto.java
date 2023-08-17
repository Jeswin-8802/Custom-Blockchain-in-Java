package io.mycrypto.webrtc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class InitiateDto {

    @JsonProperty("from")
    private String client;

    @JsonProperty("to")
    private String initiateTo;

    public InitiateDto() {
        // empty constructor needed to deserialize
    }

    public InitiateDto(String client, String initiateTo) {
        this.client = client;
        this.initiateTo = initiateTo;
    }
}
