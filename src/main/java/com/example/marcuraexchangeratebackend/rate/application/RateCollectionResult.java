package com.example.marcuraexchangeratebackend.rate.application;

import java.time.LocalDate;

/**
 * Result of a rate collection operation.
 * Contains statistics about the number of rates inserted and updated.
 *
 * @param rateDate the date of the rate snapshot
 * @param baseCurrency the base currency (e.g., EUR, USD)
 * @param totalRates total number of rates in the snapshot
 * @param inserted number of new rates inserted
 * @param updated number of existing rates updated
 */
public record RateCollectionResult(
        LocalDate rateDate,
        String baseCurrency,
        int totalRates,
        int inserted,
        int updated
) {
}
