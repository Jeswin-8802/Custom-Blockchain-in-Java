package io.mycrypto.dto;

import lombok.Data;
import lombok.ToString;
import org.springframework.stereotype.Component;

@Data
@ToString
public class CreateWalletRequestDto {
    String walletName;
}
