package io.mycrypto.webrtc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class IceRequestDto {
    @JsonProperty("to-dodo-address")
    private String to;

    public IceRequestDto() {
        // empty constructor needed to deserialize
    }

    public IceRequestDto(String to) {
        this.to = to;
    }
}
