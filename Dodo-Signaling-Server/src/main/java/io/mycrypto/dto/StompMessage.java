package io.mycrypto.dto;

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
    private long date;

    public StompMessage() {
        id = String.valueOf(UUID.randomUUID());
        date = new Date().getTime();
    }

    public StompMessage(String from, MessageType type, String message) {
        this.id = String.valueOf(UUID.randomUUID());
        this.from = from;
        this.type = type;
        this.message = message;
        this.date = new Date().getTime();
    }

    @Override
    public String toString() {
        return String.format("""
                            {
                                "id": "%s",
                                "from": "%s",
                                "type": "%s",
                                "payload": "%s",
                                "date": "%s"
                            }
                        """,
                id,
                from,
                type.toString(),
                message,
                new Date(date)
        );
    }
}
