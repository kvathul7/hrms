package com.axvon.hrms.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Which model is answering, and whether it can actually be reached.
 *
 * Two providers are on the classpath and {@code spring.ai.model.chat} picks one. They
 * fail in completely different ways, and the operator needs to be told which:
 *
 *  - <b>ollama</b> needs no key, but needs the Ollama service running locally with a
 *    tool-capable model pulled. "Set an API key" would be useless advice here.
 *  - <b>anthropic</b> needs no local service, but needs ANTHROPIC_API_KEY.
 *
 * So the reason an assistant is unavailable is computed here and handed to the frontend
 * verbatim, rather than the panel guessing from a bare boolean.
 */
@Component
@Slf4j
public class AssistantProvider {

    /** Matches the fallback in application.properties. Never a usable key. */
    static final String NOT_CONFIGURED = "not-configured";

    /** Long enough for a local service to answer, short enough not to stall the panel. */
    private static final Duration PROBE_TIMEOUT = Duration.ofMillis(1500);

    /** A probe result is reused for this long; /chat/status is polled by the UI. */
    private static final long PROBE_CACHE_MS = 15_000;

    private final String provider;
    private final String anthropicKey;
    private final String ollamaUrl;
    private final String ollamaModel;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(PROBE_TIMEOUT).build();

    private volatile long lastProbeAt = 0;
    private volatile Boolean lastProbe = null;

    public AssistantProvider(
            @Value("${spring.ai.model.chat:anthropic}") String provider,
            @Value("${spring.ai.anthropic.api-key:}") String anthropicKey,
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaUrl,
            @Value("${spring.ai.ollama.chat.options.model:}") String ollamaModel) {
        this.provider = provider == null ? "anthropic" : provider.trim().toLowerCase();
        this.anthropicKey = anthropicKey;
        this.ollamaUrl = ollamaUrl == null ? "" : ollamaUrl.trim().replaceAll("/+$", "");
        this.ollamaModel = ollamaModel == null ? "" : ollamaModel.trim();
    }

    public String name() {
        return provider;
    }

    public boolean isOllama() {
        return "ollama".equals(provider);
    }

    /** The model that will answer, for display in the panel. */
    public String model() {
        return isOllama() ? ollamaModel : "claude";
    }

    /**
     * Whether the assistant is set up in principle.
     *
     * For Ollama this is always true — nothing has to be configured, only running, and
     * that is a separate question answered by {@link #isReachable()}. Treating
     * "not running" as "not configured" would tell the operator to set a key they do
     * not need.
     */
    public boolean isConfigured() {
        return isOllama() || hasAnthropicKey();
    }

    private boolean hasAnthropicKey() {
        return anthropicKey != null
            && !anthropicKey.isBlank()
            && !NOT_CONFIGURED.equals(anthropicKey.trim());
    }

    /**
     * Is the local Ollama service answering? Cached briefly, since the chat panel polls
     * status and a dead service costs a full connect timeout each time.
     *
     * Always true for Anthropic: reachability there is a network question that is not
     * worth a synchronous probe on every status call, and a genuine outage surfaces as a
     * readable message on the first question instead.
     */
    public boolean isReachable() {
        if (!isOllama()) return true;
        long now = System.currentTimeMillis();
        Boolean cached = lastProbe;
        if (cached != null && now - lastProbeAt < PROBE_CACHE_MS) return cached;
        boolean up = probeOllama();
        lastProbe = up;
        lastProbeAt = now;
        return up;
    }

    private boolean probeOllama() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(ollamaUrl + "/api/tags"))
                .timeout(PROBE_TIMEOUT).GET().build();
            HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
            return res.statusCode() >= 200 && res.statusCode() < 400;
        } catch (Exception e) {
            // Not an error worth a stack trace: "Ollama is not installed yet" is the
            // normal state of a fresh installation.
            log.debug("HR Assistant: Ollama not reachable at {} ({})", ollamaUrl, e.getMessage());
            return false;
        }
    }

    /** Null when the assistant can answer; otherwise what the operator has to fix. */
    public String unavailableReason() {
        return reasonFor(provider, isConfigured(), isReachable(), ollamaUrl, ollamaModel);
    }

    /**
     * Pure so it can be asserted without booting a context or installing Ollama. The
     * wording is shown to the operator as-is, in the chat panel.
     */
    public static String reasonFor(String provider, boolean configured, boolean reachable,
                                   String ollamaUrl, String ollamaModel) {
        if ("ollama".equals(provider)) {
            if (!reachable)
                return "The assistant uses Ollama, which runs the model on this computer, "
                    + "and it is not responding at " + ollamaUrl + ". Install it from ollama.com, "
                    + "then run: ollama pull " + (ollamaModel.isBlank() ? "qwen2.5:7b" : ollamaModel);
            return null;
        }
        if (!configured)
            return "The HR Assistant is not switched on for this installation. "
                + "An administrator needs to set the ANTHROPIC_API_KEY environment variable.";
        return null;
    }
}
