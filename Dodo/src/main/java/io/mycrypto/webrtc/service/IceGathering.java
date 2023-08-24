package io.mycrypto.webrtc.service;

import de.javawi.jstun.attribute.ChangeRequest;
import de.javawi.jstun.attribute.MappedAddress;
import de.javawi.jstun.attribute.MessageAttribute;
import de.javawi.jstun.attribute.MessageAttributeParsingException;
import de.javawi.jstun.header.MessageHeader;
import de.javawi.jstun.util.UtilityException;
import io.mycrypto.webrtc.entity.IceCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;

@Slf4j
@Service
public class IceGathering {
    private Set<String> stunServers = new HashSet<>(Arrays.asList(
            "stun.l.google.com",
            "stun1.l.google.com",
            "stun2.l.google.com",
            "stun3.l.google.com",
            "stun4.l.google.com")
    );

    public IceCandidate getIceCandidate() {
        for (String server: stunServers) {
            IceCandidate iceCandidate = getIceCandidate(server);
            if (iceCandidate != null)
                return iceCandidate;
        }
        return null;
    }

    private IceCandidate getIceCandidate(String server) {
        try {
            MessageHeader sendMH = new MessageHeader(MessageHeader.MessageHeaderType.BindingRequest);
            sendMH.generateTransactionID();

            ChangeRequest changeRequest = new ChangeRequest();
            sendMH.addMessageAttribute(changeRequest);

            byte[] data = sendMH.getBytes();

            DatagramSocket socket = new DatagramSocket();
            socket.setReuseAddress(true);

            DatagramPacket p = new DatagramPacket(data, data.length, InetAddress.getByName(server), 19302);
            socket.send(p);

            DatagramPacket rp;

            rp = new DatagramPacket(new byte[32], 32);

            socket.receive(rp);
            MessageHeader receiveMH = new MessageHeader(MessageHeader.MessageHeaderType.BindingResponse);

            log.debug("tnxId: {}, Size: {}", Arrays.toString(receiveMH.getTransactionID()), receiveMH.getTransactionID().length);

            receiveMH.parseAttributes(rp.getData());
            MappedAddress ma = (MappedAddress) receiveMH
                    .getMessageAttribute(MessageAttribute.MessageAttributeType.MappedAddress);

            log.info("Address: {}, Port: {}", ma.getAddress(), ma.getPort());

            IceCandidate iceCandidate = new IceCandidate();
            iceCandidate.setServer(server);
            iceCandidate.setAddress(String.valueOf(ma.getAddress().getInetAddress()));
            iceCandidate.setPort(ma.getPort());

            return iceCandidate;

        } catch (UtilityException | SocketException | MessageAttributeParsingException exception) {
            log.error("An unexpected error occurred while gathering ICE candidates", exception);
        } catch (IOException exception) {
            log.error("IOException occurred when sending/receiving packets to STUN server", exception);
        }
        return null;
    }
}
