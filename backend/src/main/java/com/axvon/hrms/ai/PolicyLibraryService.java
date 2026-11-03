package com.axvon.hrms.ai;

import com.axvon.hrms.service.AuditService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Ingests company policy PDFs and retrieves the passages that answer a question.
 *
 * The pipeline is the ordinary RAG one: read the PDF page by page, split each page into
 * overlapping token-bounded chunks, embed every chunk with the local ONNX model, and put
 * it in the index with enough metadata to cite it afterwards — file name, page number and
 * the nearest heading above the text.
 *
 * Citing is the point. An HR answer that cannot be traced to a page of a named document
 * is worse than no answer, because somebody will act on it. So the chunk metadata is
 * carried all the way back into the chat response (see {@link HrAssistantService}), and
 * when nothing clears the similarity threshold the assistant is told to say the policies
 * do not cover it rather than fall back on what a language model happens to believe about
 * notice periods in general.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyLibraryService {

    /** Same ceiling as EmployeeDocument (rule 31), for the same reason: it is a DB/heap row. */
    private static final long MAX_BYTES = 8L * 1024 * 1024;

    /** Chunk size in tokens. Big enough to hold a clause with its context, small enough to cite precisely. */
    private static final int CHUNK_TOKENS = 400;

    /** Below this cosine similarity a chunk is noise, and a wrong citation is worse than none. */
    private static final double MIN_SIMILARITY = 0.45;

    private static final int TOP_K = 5;

    static final String META_FILE = "fileName";
    static final String META_TITLE = "documentTitle";
    static final String META_PAGE = "page";
    static final String META_SECTION = "section";

    /**
     * Resolved on use, never injected directly: a plain field would force the @Lazy
     * vector store — and the ONNX model behind it — to be built during startup, which
     * is the 45 seconds Electron's launch timeout cannot afford (see PolicyStoreConfig).
     */
    private final ObjectProvider<SimpleVectorStore> storeProvider;
    private final AuditService audit;
    private final ObjectMapper json;
    private final AiPaths paths;

    /** Catalogue of what has been ingested. The vector store cannot be enumerated. */
    private final Map<String, PolicyDocument> catalogue = new LinkedHashMap<>();
    private boolean catalogueLoaded = false;

    // ── ingest ───────────────────────────────────────────────────────────────────

    /**
     * Chunk, embed and index one policy PDF, replacing any earlier upload of the same
     * file name.
     *
     * @throws IllegalArgumentException on anything the operator can fix — wrong type, too
     *         big, unreadable, or a PDF with no extractable text (a scan).
     */
    public synchronized PolicyDocument ingest(MultipartFile file, String title) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choose a PDF file to upload");
        if (file.getSize() > MAX_BYTES)
            throw new IllegalArgumentException("That file is " + mb(file.getSize())
                + " MB. The limit is " + (MAX_BYTES / 1024 / 1024) + " MB.");

        String name = safeName(file.getOriginalFilename());
        if (!name.toLowerCase().endsWith(".pdf"))
            throw new IllegalArgumentException("Only PDF policy documents can be indexed — \"" + name + "\" is not a PDF.");

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read the uploaded file: " + e.getMessage());
        }
        // Cheap sanity check before handing it to PDFBox, which throws a less helpful error.
        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F')
            throw new IllegalArgumentException("\"" + name + "\" is not a valid PDF file.");

        List<Document> pages;
        try {
            // ByteArrayResource keeps the upload off disk. The reader names the resource
            // in its metadata, so the filename has to be carried by the resource itself.
            pages = new PagePdfDocumentReader(new NamedByteArrayResource(bytes, name)).read();
        } catch (Exception e) {
            log.warn("HR Assistant: PDFBox rejected {} ({})", name, e.getMessage());
            throw new IllegalArgumentException("\"" + name + "\" could not be read as a PDF. "
                + "If it came from a scanner it may hold images rather than text, which cannot be indexed.");
        }

        String docTitle = (title == null || title.isBlank()) ? stripExtension(name) : title.trim();
        List<Document> chunks = new ArrayList<>();
        TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(CHUNK_TOKENS)
            .withKeepSeparator(true)
            .build();

        int maxPage = 0;
        for (Document page : pages) {
            int pageNo = pageNumberOf(page);
            maxPage = Math.max(maxPage, pageNo);
            if (page.getText() == null || page.getText().isBlank()) continue;
            for (Document chunk : splitter.apply(List.of(page))) {
                // PDF text extraction is positional, so a justified or tabulated line comes
                // back as "period   of 60  days". Left alone those runs of spaces end up in
                // the embedding, in the heading and in the quoted excerpt — they make the
                // citation look broken and they are not in the question anybody types.
                String text = tidy(chunk.getText());
                if (text.isBlank()) continue;
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put(META_FILE, name);
                meta.put(META_TITLE, docTitle);
                meta.put(META_PAGE, pageNo);
                meta.put(META_SECTION, headingIn(text));
                chunks.add(new Document(text, meta));
            }
        }
        if (chunks.isEmpty())
            throw new IllegalArgumentException("No readable text was found in \"" + name + "\". "
                + "A scanned PDF has to be run through OCR before it can be indexed.");

        loadCatalogue();
        if (catalogue.containsKey(name)) removeFromIndex(name);     // re-upload replaces

        storeProvider.getObject().add(chunks);
        PolicyDocument record = new PolicyDocument(name, docTitle, chunks.size(), maxPage,
            LocalDateTime.now().toString(), audit.currentUser());
        catalogue.put(name, record);
        persist();

        audit.log("POLICY_INDEXED", "PolicyDocument", null, null,
            docTitle + " · " + name + " · " + chunks.size() + " chunks");
        log.info("HR Assistant: indexed {} ({} chunks over {} pages)", name, chunks.size(), maxPage);
        return record;
    }

    // ── read ─────────────────────────────────────────────────────────────────────

    public synchronized List<PolicyDocument> list() {
        loadCatalogue();
        return List.copyOf(catalogue.values());
    }

    public synchronized boolean isEmpty() {
        loadCatalogue();
        return catalogue.isEmpty();
    }

    /** The passages most likely to answer `question`, best first. Empty when none clear the bar. */
    public List<Document> search(String question) {
        if (question == null || question.isBlank() || isEmpty()) return List.of();
        try {
            List<Document> hits = storeProvider.getObject().similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(TOP_K)
                .similarityThreshold(MIN_SIMILARITY)
                .build());
            return hits == null ? List.of() : hits;
        } catch (Exception e) {
            // A broken index must not take the chat down — the assistant can still use
            // its tools and will simply say the policies do not cover the question.
            log.warn("HR Assistant: policy search failed ({})", e.getMessage());
            return List.of();
        }
    }

    // ── delete ───────────────────────────────────────────────────────────────────

    public synchronized boolean delete(String fileName) {
        loadCatalogue();
        String name = safeName(fileName);
        if (!catalogue.containsKey(name)) return false;
        PolicyDocument gone = catalogue.remove(name);
        removeFromIndex(name);
        persist();
        audit.log("POLICY_REMOVED", "PolicyDocument", null, gone.title() + " · " + name, null);
        return true;
    }

    private void removeFromIndex(String fileName) {
        try {
            // Filter-expression delete, so the chunk ids never have to be tracked.
            storeProvider.getObject().delete("%s == '%s'".formatted(META_FILE, fileName.replace("'", "''")));
        } catch (Exception e) {
            log.warn("HR Assistant: could not drop the old chunks for {} ({})", fileName, e.getMessage());
        }
    }

    // ── persistence ──────────────────────────────────────────────────────────────

    private void persist() {
        try {
            Files.createDirectories(paths.directory());
            storeProvider.getObject().save(paths.indexFile());
            json.writerWithDefaultPrettyPrinter()
                .writeValue(paths.catalogueFile(), new ArrayList<>(catalogue.values()));
        } catch (Exception e) {
            log.error("HR Assistant: the policy index could not be saved to disk ({}). "
                + "It is live for this run but will be lost on restart.", e.getMessage());
        }
    }

    private void loadCatalogue() {
        if (catalogueLoaded) return;
        catalogueLoaded = true;
        var f = paths.catalogueFile();
        if (!f.isFile()) return;
        try {
            for (PolicyDocument d : json.readValue(f, new TypeReference<List<PolicyDocument>>() {}))
                catalogue.put(d.fileName(), d);
        } catch (Exception e) {
            log.warn("HR Assistant: could not read the policy catalogue ({}) — re-upload to rebuild it", e.getMessage());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static int pageNumberOf(Document page) {
        Object p = page.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER);
        if (p instanceof Number n) return n.intValue();
        try {
            return p == null ? 0 : Integer.parseInt(p.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Collapses runs of spaces/tabs inside each line, keeping the line structure. */
    static String tidy(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\\R", -1)) {
            String clean = line.replaceAll("[ \\t\\u00A0]{2,}", " ").strip();
            sb.append(clean).append('\n');
        }
        // Three or more blank lines in a row carry no meaning either.
        return sb.toString().replaceAll("\\n{3,}", "\n\n").strip();
    }

    /**
     * The nearest thing to a section heading in a chunk, so a citation can read
     * "4. Notice Period" rather than only a page number. Headings in policy documents
     * are short lines, often numbered, and rarely end in a full stop.
     *
     * A numbered heading wins over a banner line: on page 1 of a handbook the first
     * heading-like line is the document's own title ("AXVON POLICY HANDBOOK"), which is
     * useless as a locator when the chunk is really about section 4.
     */
    static String headingIn(String text) {
        String fallback = null;
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.length() < 3 || line.length() > 80) continue;
            if (line.endsWith(".") && !line.matches("^\\d+(\\.\\d+)*\\..*")) continue;
            if (line.matches("^(\\d+(\\.\\d+)*|[A-Z]|[IVXLC]+)[.)]\\s+\\S.*")) return line;
            boolean shouty = line.equals(line.toUpperCase()) && line.matches(".*[A-Z]{3,}.*");
            boolean titled = line.matches("^([A-Z][\\w/&'-]*)(\\s+(of|and|the|for|to|in|on|a|an)\\s+|\\s+)?([A-Z][\\w/&'-]*\\s*)+$");
            if ((shouty || titled) && fallback == null) fallback = line;
        }
        return fallback;
    }

    /** Defence in depth, same reasoning as safeReportName() in rule 21. */
    static String safeName(String raw) {
        if (raw == null || raw.isBlank()) return "policy.pdf";
        String base = raw.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[^A-Za-z0-9._ -]", "_").strip();
        if (base.isEmpty() || base.equals(".") || base.equals("..")) return "policy.pdf";
        return base.length() > 120 ? base.substring(0, 120) : base;
    }

    private static String stripExtension(String n) {
        int dot = n.lastIndexOf('.');
        return dot <= 0 ? n : n.substring(0, dot);
    }

    private static String mb(long bytes) {
        return String.format("%.1f", bytes / 1024.0 / 1024.0);
    }

    /** PagePdfDocumentReader reads the file name off the resource, so it must have one. */
    private static class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }

    /** One ingested policy document, as listed in the admin screen. */
    public record PolicyDocument(String fileName, String title, int chunks, int pages,
                                 String indexedAt, String indexedBy) {}
}
