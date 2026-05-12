package com.mauricio.placarvolei.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Utilitários pra descobrir IP local da máquina na rede LAN.
 * Filtra loopback, interfaces desativadas e IPv6.
 */
public final class IpUtils {

    private IpUtils() {}

    /**
     * Retorna primeiro IPv4 não-loopback encontrado, ou "0.0.0.0" se falhar.
     */
    public static String getLocalIpv4() {
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;
                Enumeration<InetAddress> addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr.isLoopbackAddress()) continue;
                    String host = addr.getHostAddress();
                    if (host == null) continue;
                    // só IPv4
                    if (host.indexOf(':') < 0) return host;
                }
            }
        } catch (Exception ignored) {}
        return "0.0.0.0";
    }
}
