package io.mycrypto.webrtc.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.UUID;

@Data
@Slf4j
public class StompMessage {
    private String id;
    private String from;
    private MessageType type;
    private String message;
    private Long date;

    public StompMessage() {
        id = String.valueOf(UUID.randomUUID());
        message = "";
        date = new Date().getTime();
    }

    @Override
    public String toString() {
        ObjectMapper mapper = new ObjectMapper();
        Object json;
        try {
            json = mapper.readValue(message, Object.class);
            return String.format("""
                                {
                                    "id": %s,
                                    "from": %s,
                                    "type": %s,
                                    "payload": %s,
                                    "date": %s
                                }
                            """,
                    id,
                    from,
                    type.toString(),
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json),
                    new Date(date)
            );
        } catch (JsonProcessingException exception) {
            log.error("Encountered an error when converting payload to json", exception);
        }
        return null;
    }
}
