package io.mycrypto.util;

import io.mycrypto.dto.StoreUtxoSumInfo;
import io.mycrypto.dto.UTXODto;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

@Slf4j
public class UTXOFilterAlgorithms {

    // Algorithms to selectively choose UTXOs that adds to or is greater than a target amount

    // ------------MEET_IN_THE_MIDDLE-----------------------------------------------------------------------------------------------------------

    /**
     * refer : <a href="https://www.baeldung.com/cs/subset-of-numbers-closest-to-target">Subset of numbers closest to a target</a>
     *
     * @param allUTXOs all UTXOs associated with a wallet
     * @param amount Amount to transact
     * @return A list of Filtered UTXOs whose sum is greater than or equal to the amount
     */
    public static List<UTXODto> meetInTheMiddleSelectionAlgorithm(List<UTXODto> allUTXOs, BigDecimal amount) {
        List<StoreUtxoSumInfo> first = generate(allUTXOs, 0, allUTXOs.size() / 2, StoreUtxoSumInfo.builder().build(), amount);
        List<StoreUtxoSumInfo> second = generate(allUTXOs, allUTXOs.size() / 2, allUTXOs.size(), StoreUtxoSumInfo.builder().build(), amount);

        second.sort(Comparator.comparing(StoreUtxoSumInfo::getSum));

        StoreUtxoSumInfo result = StoreUtxoSumInfo.builder()
                .sum(new BigDecimal(Integer.MAX_VALUE))
                .build();
        for (StoreUtxoSumInfo utxoSumInfo: first) {
            StoreUtxoSumInfo temp = binarySearchOnUtxoInfoList(second, amount.subtract(utxoSumInfo.getSum()) /* (amount - sum of first subset sum combination sequence from the first list) */);
            if (temp.getSum().compareTo(new BigDecimal(0)) >= 0 && (temp.getSum().add(utxoSumInfo.getSum())).compareTo(result.getSum()) < 0) {
                utxoSumInfo.getIndexes().addAll(temp.getIndexes());
                result.setIndexes(utxoSumInfo.getIndexes());
                result.setSum(utxoSumInfo.getSum().add(temp.getSum()));
            }
        }

        List<UTXODto> filteredUTXOs = new ArrayList<>();
        for (int i: result.getIndexes())
            filteredUTXOs.add(allUTXOs.get(i));

        return filteredUTXOs;
    }

    /**
     * generates all possible combinations of sums of UTXOs
     *
     * @param utxos The Base List that contains all the UTXOs from the wallet
     * @param i current pos
     * @param end end position
     * @param utxoSumInfo Stores the sum as well the indexes of the values that contribute to the sum
     * @param targetAmount The least amount the utxo subset must add up to
     * @return A list of All possible subset sum combinations stored as <StoreUtxoSumInfo.class>
     */
    public static List<StoreUtxoSumInfo> generate(List<UTXODto> utxos, int i, int end, StoreUtxoSumInfo utxoSumInfo, BigDecimal targetAmount) {
        List<StoreUtxoSumInfo> result;
        if (i == end) {
            result = new ArrayList<>();
            if ((utxoSumInfo.getSum()).compareTo(targetAmount) >= 0)
                result.add(utxoSumInfo);
            return result;
        }

        List<Integer> indexes = new ArrayList<>(utxoSumInfo.getIndexes());
        indexes.add(i);
        StoreUtxoSumInfo newStorageObject = StoreUtxoSumInfo.builder()
                .indexes(indexes)
                .sum(utxoSumInfo.getSum().add(utxos.get(i).getAmount()))
                .build();
        List<StoreUtxoSumInfo> pick = generate(utxos, i + 1, end, newStorageObject, targetAmount);

        List<StoreUtxoSumInfo> leave = generate(utxos, i + 1, end, utxoSumInfo, targetAmount);

        List<StoreUtxoSumInfo> mergedList = new ArrayList<>();
        mergedList.addAll(pick);
        mergedList.addAll(leave);

        return mergedList;
    }

    /**
     * Performs binary search on List of UTXO subset sum Info
     *
     * @param utxoInfoList list to search in
     * @param key key to search
     * @return UTXO subset sum Info with an amount closest to the key
     */
    public static StoreUtxoSumInfo binarySearchOnUtxoInfoList(List<StoreUtxoSumInfo> utxoInfoList, BigDecimal key) {

        int low = 0;
        int high = utxoInfoList.size() - 1;
        int mid = 0;
        while (low <= high) {
            mid = low  + ((high - low) / 2);
            int compareKey = key.compareTo(utxoInfoList.get(mid).getSum());
            if (compareKey == 0)
                return utxoInfoList.get(mid);
            else if (compareKey < 0)
                high = mid - 1;
            else
                low = mid + 1;
        }

        // if key > list[mid]
        if (key.compareTo(utxoInfoList.get(mid).getSum()) > 0)
            return StoreUtxoSumInfo.builder()
                    .sum(new BigDecimal(-1))
                    .build();
        return utxoInfoList.get(mid);
    }

    // ------------HIGHEST_SORTED/LOWEST_SORTED-------------------------------------------------------------------------------------------------

    public static List<UTXODto> selectUTXOsInSortedOrder(List<UTXODto> allUTXOs, BigDecimal amount, Boolean order) {
        if (order)
            allUTXOs.sort((a, b) -> b.getAmount().compareTo(a.getAmount()));
        else
            allUTXOs.sort(Comparator.comparing(UTXODto::getAmount));

        List<UTXODto> filteredUTXOs = new ArrayList<>();

        BigDecimal total = new BigDecimal(0);
        for (UTXODto utxo: allUTXOs) {
            total = total.add(utxo.getAmount());
            filteredUTXOs.add(utxo);
            if (total.add(utxo.getAmount()).compareTo(amount) >= 0)
                break;
        }

        return filteredUTXOs;
    }

    // ------------RANDOMIZED----------------------------------------------------------------------------------------------------------------

    public static List<UTXODto> selectRandomizedUTXOs(List<UTXODto> allUTXOs, BigDecimal amount) {
        List<Integer> selectedIndexes = new ArrayList<>();
        BigDecimal total = new BigDecimal(0);
        Random rand = new Random();

        while (total.compareTo(amount) < 0) {
            int randomPosition = rand.nextInt(allUTXOs.size());
            if (!selectedIndexes.contains(randomPosition)) {
                selectedIndexes.add(randomPosition);
                total = total.add(allUTXOs.get(randomPosition).getAmount());
            }
        }

        List<UTXODto> result = new ArrayList<>();
        for (int i: selectedIndexes)
            result.add(allUTXOs.get(i));

        return result;
    }
}
