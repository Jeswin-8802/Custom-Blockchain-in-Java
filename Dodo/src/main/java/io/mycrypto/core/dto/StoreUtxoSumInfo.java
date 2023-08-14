package io.mycrypto.core.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class StoreUtxoSumInfo {
    List<Integer> indexes;
    BigDecimal sum;
}
