package io.mycrypto.webrtc.controller;

import io.mycrypto.webrtc.dto.StompMessage;
import io.mycrypto.webrtc.service.MessageProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class DodoServerController {
    /*
    // for testing   http://jxy.me/websocket-debug-tool/
        ws://localhost:8082/dodo-p2p
        /peer/queue/reply       (subscribe)
        /dodo/message           (destination)
        {"id":"sasa-242dsdsr3","from":"nobody","type":"ICE","message":"{\"hello\": \"world\"}","date":1692208415000}
     */


    @Autowired
    private MessageProcessor msgProcessor;

    @MessageMapping("/message")
    public void processMessageFromPeer(@Payload StompMessage message,
                                       Principal principal,
                                       @Header("simpSessionId") String sessionId) throws Exception {
        msgProcessor.processMessageFromPeerAsServer(sessionId, principal.getName(), message);
    }
}
