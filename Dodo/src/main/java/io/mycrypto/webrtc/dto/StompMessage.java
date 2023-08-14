package io.mycrypto.webrtc.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;

@Slf4j
@Data
public class StompMessage {
    private String id;
    private String type;
    private String message;
    private LocalDate date;

    @Override
    public String toString() {
        ObjectMapper mapper = new ObjectMapper();
        Object json;
        try {
            json = mapper.readValue(message, Object.class);
            return String.format("""
                    {
                        "id": %s,
                        "type": %s,
                        "payload": %s,
                        "date": %s
                    }
                """,
                    id,
                    type,
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json),
                    date
            );
        } catch (JsonProcessingException e) {
            log.error("Encountered an error when converting payload to json");
        }
        return null;
    }
}
