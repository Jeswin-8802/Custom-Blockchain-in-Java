package io.mycrypto.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.math.BigDecimal;

@Getter
@Configuration
@PropertySource("classpath:config.properties")
public class DodoCommonConfig {
    @Value("${ADMIN_ADDRESS}")
    private String adminAddress;

    @Value("${BLOCK_REWARD}")
    private BigDecimal blockReward;

    @Value("${TRANSACTIONS_COUNT_LOWER_LIMIT}")
    private Integer lowerLimitCount;

    @Value("${TRANSACTIONS_COUNT_UPPER_LIMIT}")
    private Integer upperLimitCount;

    @Value("${INJECT_CURRENCY_IF_ADMIN:0}")
    private Integer injectIfAdmin;

    @Value("${USER_SATURATION_LIMIT}")
    private Integer userSaturationLimit;

    @Value("${DEFAULT_TRANSACTION_FEE:0.0001}")
    private BigDecimal transactionFee;
}
