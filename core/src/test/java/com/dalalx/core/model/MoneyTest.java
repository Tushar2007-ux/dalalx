package com.dalalx.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @ParameterizedTest(name = "{0} paise -> {1}")
    @CsvSource(delimiter = '|', value = {
            "0                   | ₹0.00",
            "1                   | ₹0.01",
            "99                  | ₹0.99",
            "100                 | ₹1.00",
            "10250               | ₹102.50",
            "99999               | ₹999.99",
            "100000              | ₹1,000.00",
            "1234567             | ₹12,345.67",
            "29778000            | ₹2,97,780.00",
            "10000000            | ₹1,00,000.00",
            "1000000000          | ₹1,00,00,000.00",
            "12345678901         | ₹12,34,56,789.01",
            "-10250              | -₹102.50",
            "-5                  | -₹0.05",
            "-29778000           | -₹2,97,780.00",
            "9223372036854775807 | ₹92,23,37,20,36,85,47,758.07",
            "-9223372036854775808| -₹92,23,37,20,36,85,47,758.08",
    })
    void formatsInIndianGrouping(long paise, String expected) {
        assertThat(Money.format(paise)).isEqualTo(expected);
    }

    @Test
    void ofRupeesConvertsToPaise() {
        assertThat(Money.ofRupees(102, 50)).isEqualTo(10250);
        assertThat(Money.ofRupees(0, 0)).isZero();
        assertThat(Money.ofRupees(297780, 0)).isEqualTo(29778000);
    }

    @Test
    void ofRupeesRejectsInvalidParts() {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.ofRupees(-1, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> Money.ofRupees(1, -1));
        assertThatIllegalArgumentException().isThrownBy(() -> Money.ofRupees(1, 100));
    }

    @Test
    void ofRupeesThrowsOnOverflow() {
        assertThatThrownBy(() -> Money.ofRupees(Long.MAX_VALUE, 0)).isInstanceOf(ArithmeticException.class);
    }
}
