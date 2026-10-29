package com.backlogtracker.commons.keepalive;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class RenderKeepAliveTest {

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            lastPath.set(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void pingsTheHealthEndpointOfTheConfiguredPublicUrl() {
        RenderKeepAlive keepAlive = new RenderKeepAlive(baseUrl + "/", true);

        keepAlive.ping();

        assertThat(hits.get()).isEqualTo(1);
        assertThat(lastPath.get()).isEqualTo("/actuator/health");
    }

    @Test
    void doesNothingWithoutAUrlOrWhenDisabled() {
        RenderKeepAlive noUrl = new RenderKeepAlive("", true);
        RenderKeepAlive disabled = new RenderKeepAlive(baseUrl, false);

        noUrl.ping();
        disabled.ping();

        assertThat(noUrl.enabled()).isFalse();
        assertThat(disabled.enabled()).isFalse();
        assertThat(hits.get()).isZero();
    }

    @Test
    void anUnreachableServerIsSwallowedNotThrown() {
        server.stop(0);
        RenderKeepAlive keepAlive = new RenderKeepAlive(baseUrl, true);

        keepAlive.ping(); // must not throw: the scheduler would just log and retry anyway
    }
}
