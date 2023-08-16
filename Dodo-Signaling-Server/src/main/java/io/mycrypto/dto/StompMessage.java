package io.mycrypto.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Slf4j
public class StompMessage {
    private String id;
    private String from;
    private MessageType type;
    private String message;
    private LocalDate date;

    public StompMessage() {
        id = String.valueOf(UUID.randomUUID());
        message = "";
        date = LocalDate.now();
    }

    public StompMessage(String from, MessageType type, String message) {
        this.id = String.valueOf(UUID.randomUUID());
        this.from = from;
        this.type = type;
        this.message = message;
        this.date = LocalDate.now();
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
