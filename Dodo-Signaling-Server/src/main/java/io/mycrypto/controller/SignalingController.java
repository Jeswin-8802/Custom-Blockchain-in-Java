package io.mycrypto.controller;

import io.mycrypto.dto.StompMessage;
import io.mycrypto.service.MessageHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class SignalingController {
    @Autowired
    MessageHandler msgHandler;

    @MessageMapping("/message")
    public void processMessageFromPeer(@Payload StompMessage message,
                                       Principal principal,
                                       @Header("simpSessionId") String sessionId) throws Exception {
        msgHandler.processMessageFromPeer(sessionId, principal.getName(), message);
    }
}
