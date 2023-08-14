package io.mycrypto.webrtc.controller;

import io.mycrypto.webrtc.dto.StompMessage;
import io.mycrypto.webrtc.service.MessageProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class DodoServerController {
    // for testing   http://jxy.me/websocket-debug-tool/

    @Autowired
    private MessageProcessor msgProcessor;

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    @MessageMapping("/message")
    public void processMessageFromPeer(@Payload StompMessage message,
                                       Principal principal,
                                       @Header("simpSessionId") String sessionId) throws Exception {
        msgProcessor.processMessageFromPeer(sessionId, principal.getName(), message);
    }

    public void sendMessageToPeer(String sendTo) {
        this.simpMessagingTemplate.convertAndSendToUser(sendTo, "/queue/reply", "received");
    }
}
