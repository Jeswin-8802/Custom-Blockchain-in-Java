package io.mycrypto.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class InitiateDto {

    @JsonProperty("to")
    private String initiateTo;

    public InitiateDto() {
        // empty constructor needed to deserialize
    }

    public InitiateDto(String initiateTo) {
        this.initiateTo = initiateTo;
    }
}
