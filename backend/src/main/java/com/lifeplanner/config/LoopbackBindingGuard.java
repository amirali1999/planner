package com.lifeplanner.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.stereotype.Component;
import java.net.InetAddress;

/**
 * The app has no authentication, so it must only listen on loopback (docs/DOMAIN_RULES.md "Privacy").
 * Fails startup if {@code server.address} is missing (= all interfaces) or not a loopback address.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class LoopbackBindingGuard implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

    private final ServerProperties serverProperties;

    public LoopbackBindingGuard(ServerProperties serverProperties) {
        this.serverProperties = serverProperties;
    }

    @Override
    public void customize(ConfigurableServletWebServerFactory factory) {
        requireLoopback(serverProperties.getAddress());
    }

    static void requireLoopback(InetAddress address) {
        if (address == null || !address.isLoopbackAddress()) {
            throw new IllegalStateException(
                    "server.address must be a loopback address (e.g. 127.0.0.1); got "
                            + (address == null ? "<all interfaces>" : address.getHostAddress())
                            + ". Non-loopback binding requires an authentication model that is out of scope.");
        }
    }
}
