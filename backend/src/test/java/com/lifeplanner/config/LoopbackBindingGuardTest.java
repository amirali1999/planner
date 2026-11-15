package com.lifeplanner.config;

import org.junit.jupiter.api.Test;
import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoopbackBindingGuardTest {

    @Test
    void acceptsIpv4AndIpv6Loopback() {
        assertThatCode(() -> LoopbackBindingGuard.requireLoopback(InetAddress.getByName("127.0.0.1")))
                .doesNotThrowAnyException();
        assertThatCode(() -> LoopbackBindingGuard.requireLoopback(InetAddress.getByName("::1")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAllInterfaces() throws Exception {
        assertThatThrownBy(() -> LoopbackBindingGuard.requireLoopback(null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> LoopbackBindingGuard.requireLoopback(InetAddress.getByName("0.0.0.0")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsLanAddress() throws Exception {
        assertThatThrownBy(() -> LoopbackBindingGuard.requireLoopback(InetAddress.getByName("192.168.1.10")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loopback");
    }
}
