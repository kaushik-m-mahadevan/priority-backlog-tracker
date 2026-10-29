package com.backlogtracker.commons.keepalive;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Keeps a Render free-tier instance from sleeping by requesting its own public URL.
 *
 * <p>Render spins a free web service down after ~15 minutes without <em>inbound</em> traffic
 * from outside, so the request has to go out through the public hostname (Render sets
 * {@code RENDER_EXTERNAL_URL}); a call to localhost would not count. It only works while the
 * instance is awake, so it prevents a sleep rather than waking a sleeping service: one
 * visit after a deploy is enough to start the loop. Inert when no URL is configured (local
 * runs, tests) or {@code app.keepalive.enabled=false}.
 */
@Component
@Slf4j
public class RenderKeepAlive {

    private final URI target;
    private final HttpClient http;

    public RenderKeepAlive(@Value("${app.keepalive.url:}") String baseUrl,
                           @Value("${app.keepalive.enabled:true}") boolean enabled) {
        this.target = enabled && baseUrl != null && !baseUrl.isBlank()
                ? URI.create(baseUrl.replaceAll("/+$", "") + "/actuator/health")
                : null;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        if (target != null) {
            log.info("Keep-alive self-ping enabled: {}", target);
        }
    }

    // Render's idle cutoff is ~15 minutes; 10 leaves room for a missed or slow tick.
    @Scheduled(initialDelayString = "${app.keepalive.initial-delay-ms:120000}",
               fixedDelayString = "${app.keepalive.interval-ms:600000}")
    public void ping() {
        if (target == null) {
            return;
        }
        try {
            HttpResponse<Void> res = http.send(
                    HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(20)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            log.debug("Keep-alive ping {} -> {}", target, res.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Keep-alive ping to {} failed: {}", target, e.toString());
        }
    }

    boolean enabled() {
        return target != null;
    }
}
