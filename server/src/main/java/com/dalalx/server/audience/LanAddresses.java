package com.dalalx.server.audience;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Finds this computer's addresses on the local network (Wi-Fi or hotspot),
 * so the screen can show a QR code that phones in the room can open.
 *
 * <p>Only private IPv4 addresses are returned (192.168.x.x, 10.x.x.x,
 * 172.16–31.x.x): those are the ones phones on the same Wi-Fi can reach.
 * The most likely one (a 192.168 home/hotspot network) comes first.
 */
public final class LanAddresses {

    private LanAddresses() {
    }

    /** @return private IPv4 addresses of interfaces that are up, best guess first */
    public static List<String> find() {
        List<String> found = new ArrayList<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        found.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            return List.of();
        }
        found.sort(Comparator.comparingInt(LanAddresses::preference));
        return List.copyOf(found);
    }

    /** Lower is better: home Wi-Fi and phone hotspots first, then office networks. */
    static int preference(String ip) {
        if (ip.startsWith("192.168.")) {
            return 0;
        }
        if (ip.startsWith("172.20.10.")) {
            return 1; // iPhone personal hotspot
        }
        return ip.startsWith("10.") ? 2 : 3;
    }
}
