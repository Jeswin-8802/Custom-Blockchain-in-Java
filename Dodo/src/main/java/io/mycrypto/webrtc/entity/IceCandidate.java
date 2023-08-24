package io.mycrypto.webrtc.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.javawi.jstun.util.Address;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;

import java.util.HashMap;
import java.util.Objects;

@Slf4j
@Data
public class IceCandidate {
    @JsonProperty("server")
    private String server;
    @JsonProperty("address")
    private String address;
    @JsonProperty("port")
    private int port;

    @Override
    public String toString() {
        return String.format("""
                Server: %s,
                Address: %s,
                Port: %s
                """, server, address, port);
    }

    public JSONObject toJsonObject() {
         return new JSONObject(
                 new HashMap<String, Object>() {{
                     put("server", server);
                     put("address", address);
                     put("port", port);
                 }}
         );
    }
}
