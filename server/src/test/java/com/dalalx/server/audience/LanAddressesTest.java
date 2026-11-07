package com.dalalx.server.audience;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LanAddressesTest {

    @Test
    void prefersHomeAndHotspotNetworks() {
        assertThat(LanAddresses.preference("192.168.1.4")).isLessThan(LanAddresses.preference("172.20.10.2"));
        assertThat(LanAddresses.preference("172.20.10.2")).isLessThan(LanAddresses.preference("10.0.0.3"));
        assertThat(LanAddresses.preference("10.0.0.3")).isLessThan(LanAddresses.preference("172.17.0.1"));
    }

    @Test
    void onlyReturnsPrivateIpv4Addresses() {
        assertThat(LanAddresses.find()).allMatch(ip -> ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")
                && (ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("172.")));
    }
}
