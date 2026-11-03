package com.axvon.hrms.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.io.File;

/**
 * The policy index: where the embedded policy chunks live between runs, and the local
 * model that embeds them.
 *
 * <h3>Why SimpleVectorStore and not pgvector</h3>
 *
 * AXVON is a double-click installer. Somebody runs the .exe, clicks Install, and the app
 * works — no database to provision, no service to start, which is exactly why the main
 * store is embedded H2 rather than MySQL (rule 2). A pgvector-backed store would hand
 * that whole problem back to the customer: install PostgreSQL, create a database, enable
 * the vector extension, keep it running, and keep its credentials somewhere. The feature
 * would be unusable for the audience the product is built for.
 *
 * SimpleVectorStore is Spring AI's in-process store. It holds the vectors in a map, scores
 * every one of them on each query, and serialises the lot to a JSON file via
 * {@code save(File)} / {@code load(File)}. Nothing to install, and it sits next to the H2
 * database under {@code ~/.axvon}.
 *
 * What that costs, honestly:
 *
 *  - <b>Everything is in heap.</b> A few hundred policy pages is fine (384 floats plus the
 *    chunk text per entry, so single-digit MB). A document set in the tens of thousands of
 *    pages would need a real vector database.
 *  - <b>Search is linear.</b> No ANN index: every query compares against every chunk.
 *    Imperceptible at this scale, quadratic trouble at a much larger one.
 *  - <b>One writer.</b> Ingest is synchronised, and the file is rewritten whole after each
 *    upload. Electron already enforces a single app instance, so this matches how the H2
 *    file is treated.
 *  - <b>Not in the backup file.</b> {@code /api/backup/export} covers the H2 tables, not
 *    this index. The PDFs are re-uploadable and the index rebuilds from them, so this is a
 *    documented limitation rather than data loss.
 *
 * If a deployment ever outgrows it, only this class changes: everything downstream talks
 * to the {@code VectorStore} interface.
 *
 * <h3>Why both beans are {@code @Lazy}</h3>
 *
 * Loading the ONNX model and unpacking its native libraries takes roughly 45 seconds the
 * first time. Electron gives the backend 60 seconds to answer {@code /dashboard/health}
 * before it gives up with "Backend failed to start in 60s", and eager initialisation
 * measured 62.5s against 16s without it — so an eagerly-loaded model does not merely slow
 * the desktop app down, it stops it launching.
 *
 * Deferring both beans puts that cost on the first policy question instead of on every
 * launch, and an installation with no policy documents never pays it at all:
 * {@code PolicyLibraryService.search} checks its catalogue and returns early before ever
 * touching the store.
 */
@Configuration
@Slf4j
public class PolicyStoreConfig {

    /**
     * The local embedding model — all-MiniLM-L6-v2 run through ONNX Runtime, in-process.
     *
     * Built here rather than by spring-ai-starter-model-transformers, whose
     * autoconfiguration is switched off via {@code spring.ai.model.embedding=none},
     * purely so it can be marked {@code @Lazy}; the autoconfigured bean is eager and
     * would push startup past Electron's launch timeout.
     *
     * Anthropic publishes no embedding model, and every hosted alternative would mean a
     * second API key plus a network round trip per chunk — neither of which an
     * offline-installable desktop app can assume. The model file ships inside the
     * spring-ai-transformers jar, so nothing is downloaded on first run.
     */
    @Bean
    @Lazy
    public EmbeddingModel policyEmbeddingModel(AiPaths paths) throws Exception {
        long started = System.currentTimeMillis();
        TransformersEmbeddingModel model = new TransformersEmbeddingModel();
        // ONNX Runtime unpacks native libraries to a cache directory. Keep it under
        // ~/.axvon beside everything else the app owns rather than in the system temp
        // folder, which a cleanup tool may empty between runs.
        model.setResourceCacheDirectory(paths.directory().resolve("model-cache").toString());
        model.afterPropertiesSet();
        log.info("HR Assistant: embedding model ready in {} ms", System.currentTimeMillis() - started);
        return model;
    }

    @Bean
    @Lazy
    public SimpleVectorStore policyVectorStore(EmbeddingModel policyEmbeddingModel, AiPaths paths) {
        SimpleVectorStore store = SimpleVectorStore.builder(policyEmbeddingModel).build();
        File f = paths.indexFile();
        if (f.isFile() && f.length() > 0) {
            try {
                store.load(f);
                log.info("HR Assistant: policy index loaded from {}", f);
            } catch (Exception e) {
                // A half-written or stale-format index must not stop the app. Starting
                // empty costs the operator a re-upload; failing here costs them the
                // whole HRMS.
                log.warn("HR Assistant: could not read the policy index at {} ({}) — starting empty", f, e.getMessage());
            }
        } else {
            log.info("HR Assistant: no policy index yet — upload policy PDFs to enable policy answers");
        }
        return store;
    }
}
