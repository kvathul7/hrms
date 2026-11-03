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
    private volatile Probe lastProbe = null;

    /**
     * What a probe of the local service found.
     *
     * The middle state is the one that matters and the one the first version missed:
     * Ollama answering does NOT mean it can answer a question. Between installing Ollama
     * and finishing `ollama pull` — several GB, so minutes — the service is up with no
     * model, and reporting that as ready makes the panel accept a question and then fail
     * with something unhelpful. Each state gets its own instruction instead.
     */
    public enum Probe { DOWN, NO_MODEL, READY }

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
        return probe() == Probe.READY;
    }

    Probe probe() {
        if (!isOllama()) return Probe.READY;
        long now = System.currentTimeMillis();
        Probe cached = lastProbe;
        if (cached != null && now - lastProbeAt < PROBE_CACHE_MS) return cached;
        Probe p = probeOllama();
        lastProbe = p;
        lastProbeAt = now;
        return p;
    }

    private Probe probeOllama() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(ollamaUrl + "/api/tags"))
                .timeout(PROBE_TIMEOUT).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 400) return Probe.DOWN;
            return hasModel(res.body(), ollamaModel) ? Probe.READY : Probe.NO_MODEL;
        } catch (Exception e) {
            // Not an error worth a stack trace: "Ollama is not installed yet" is the
            // normal state of a fresh installation.
            log.debug("HR Assistant: Ollama not reachable at {} ({})", ollamaUrl, e.getMessage());
            return Probe.DOWN;
        }
    }

    /**
     * Is the configured model among the pulled ones?
     *
     * Deliberately a substring check on the /api/tags body rather than a parsed model:
     * it only has to answer one yes/no question, and a JSON shape change in Ollama should
     * not be able to break the assistant's availability reporting. A bare name is matched
     * against "name:" too, because Ollama reports an untagged pull as "name:latest".
     */
    public static boolean hasModel(String tagsBody, String model) {
        if (tagsBody == null || model == null || model.isBlank()) return false;
        if (tagsBody.contains("\"" + model + "\"")) return true;
        return !model.contains(":") && tagsBody.contains("\"" + model + ":");
    }

    /** Null when the assistant can answer; otherwise what the operator has to fix. */
    public String unavailableReason() {
        return reasonFor(provider, isConfigured(), probe(), ollamaUrl, ollamaModel);
    }

    /**
     * Pure so it can be asserted without booting a context or installing Ollama. The
     * wording is shown to the operator as-is, in the chat panel.
     */
    public static String reasonFor(String provider, boolean configured, Probe probe,
                                   String ollamaUrl, String ollamaModel) {
        if ("ollama".equals(provider)) {
            String model = ollamaModel == null || ollamaModel.isBlank() ? "qwen2.5:7b" : ollamaModel;
            return switch (probe) {
                case DOWN -> "The assistant uses Ollama, which runs the model on this computer, "
                    + "and it is not responding at " + ollamaUrl + ". Install it from ollama.com, "
                    + "then run: ollama pull " + model;
                // Up, but with nothing to answer with. Usually a pull still in progress.
                case NO_MODEL -> "Ollama is running, but the model \"" + model + "\" is not there yet. "
                    + "Run: ollama pull " + model + " — it is a few GB, so if you have just started "
                    + "it, wait for it to finish and then reopen this page.";
                case READY -> null;
            };
        }
        if (!configured)
            return "The HR Assistant is not switched on for this installation. "
                + "An administrator needs to set the ANTHROPIC_API_KEY environment variable.";
        return null;
    }
}
