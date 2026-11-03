package com.axvon.hrms;

import com.axvon.hrms.ai.AiPaths;
import com.axvon.hrms.ai.AssistantProvider;
import com.axvon.hrms.ai.HrTools;
import com.axvon.hrms.ai.PolicyLibraryService;
import com.axvon.hrms.entity.Employee;
import com.axvon.hrms.entity.LeaveRequest;
import com.axvon.hrms.service.LeaveBalanceService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The HR Assistant: its three tools, and the boundary that keeps them pointed at the
 * signed-in employee.
 *
 * <b>Nothing here reaches api.anthropic.com.</b> The {@link ChatModel} bean is replaced by
 * a Mockito mock, so {@code /chat} exercises the real controller, the real prompt
 * assembly and the real retrieval path while the model itself is a stub. The tools are
 * called directly, which is the honest way to test them: a tool is only ever invoked by
 * the model, so driving them through a mocked model would test Mockito rather than the
 * authorisation rule that matters.
 *
 * The embedding model is deliberately NOT mocked — it is the local ONNX one, runs
 * in-process, needs no key and no network, so the policy test indexes and searches a real
 * PDF. {@code axvon.ai.index-dir} in the test properties keeps that out of ~/.axvon.
 */
class AiAssistantTest extends AxvonTestBase {

    @MockitoBean private ChatModel chatModel;

    @Autowired private HrTools tools;
    @Autowired private PolicyLibraryService policies;
    @Autowired private AiPaths aiPaths;
    /** The same service the Leave screen reads, so the tool can be checked against it. */
    @Autowired private LeaveBalanceService balances;

    /** Whatever the model is asked, it says this — the tools are what is under test. */
    private void modelSays(String reply) {
        when(chatModel.call(any(Prompt.class)))
            .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(reply)))));
    }

    @BeforeEach
    void clearPolicyIndex() {
        for (var d : policies.list()) policies.delete(d.fileName());
    }

    // ── 1. the leave-balance tool returns the right number ───────────────────────

    @Test
    @DisplayName("the leave-balance tool reports the real remaining days for the signed-in employee")
    @WithMockUser(username = "arjun@test.io", roles = "EMPLOYEE")
    void leaveBalanceToolReturnsTheRightNumber() {
        Employee me = employee("Arjun", "Mehta", "arjun@test.io", "50000");
        settings().setCasualLeaveQuota(12);
        settingsRepo.save(settings());

        // Spend 3 days of Casual the way the app does — an approved request.
        approvedLeave(me, "Casual", LocalDate.now().withMonth(3).withDayOfMonth(4),
            LocalDate.now().withMonth(3).withDayOfMonth(6));
        balances.adjust(me.getId(), "Casual", LocalDate.now().getYear(), 3);

        HrTools.LeaveBalanceReport r = tools.getMyLeaveBalance("Casual", null);

        assertThat(r.ok()).isTrue();
        assertThat(r.employee()).isEqualTo("Arjun Mehta");
        assertThat(r.balances()).singleElement().satisfies(line -> {
            assertThat(line.leaveType()).isEqualTo("Casual");
            assertThat(line.entitled()).isEqualTo(12);
            assertThat(line.used()).isEqualTo(3);
            assertThat(line.remaining()).isEqualTo(9);
        });
        // And it agrees with the service the Leave screen reads, rather than its own maths.
        assertThat(r.balances().get(0).remaining())
            .isEqualTo(balances.remaining(me.getId(), "Casual", LocalDate.now().getYear()));
    }

    @Test
    @DisplayName("the balance tool accepts how a person actually types a leave type")
    @WithMockUser(username = "arjun@test.io", roles = "EMPLOYEE")
    void leaveTypeIsNormalised() {
        employee("Arjun", "Mehta", "arjun@test.io", "50000");

        assertThat(tools.getMyLeaveBalance("casual leave", null).balances())
            .singleElement().satisfies(l -> assertThat(l.leaveType()).isEqualTo("Casual"));
        assertThat(tools.getMyLeaveBalance("comp-off", null).balances())
            .singleElement().satisfies(l -> assertThat(l.leaveType()).isEqualTo("Comp Off"));
        // Every tracked type when none is named.
        assertThat(tools.getMyLeaveBalance(null, null).balances()).hasSize(4);
    }

    @Test
    @DisplayName("an untracked leave type is explained, not reported as a zero balance")
    @WithMockUser(username = "arjun@test.io", roles = "EMPLOYEE")
    void untrackedTypeIsExplained() {
        employee("Arjun", "Mehta", "arjun@test.io", "50000");

        // Reporting "0 remaining" for Maternity would be a damaging lie — it has no quota
        // and is never deducted, so the tool has to say so instead.
        HrTools.LeaveBalanceReport r = tools.getMyLeaveBalance("Maternity", null);
        assertThat(r.ok()).isFalse();
        assertThat(r.error()).contains("no quota");
        assertThat(r.balances()).isEmpty();
    }

    // ── 2. the apply-leave tool actually persists a request ──────────────────────

    @Test
    @DisplayName("the apply-leave tool writes a real Pending request for the signed-in employee")
    @WithMockUser(username = "arjun@test.io", roles = "EMPLOYEE")
    void applyLeaveToolPersistsARequest() {
        Employee me = employee("Arjun", "Mehta", "arjun@test.io", "50000");

        HrTools.LeaveApplicationReceipt r =
            tools.applyForLeave("Casual", "2026-11-10", "2026-11-11", "Family function");

        assertThat(r.ok()).isTrue();
        assertThat(r.requestId()).isNotNull();
        assertThat(r.status()).isEqualTo("Pending");
        assertThat(r.totalDays()).isEqualTo(2);

        // The row is in H2, attached to the right person, not merely reported back.
        LeaveRequest saved = leaveRepo.findById(r.requestId()).orElseThrow();
        assertThat(saved.getEmployee().getId()).isEqualTo(me.getId());
        assertThat(saved.getLeaveType()).isEqualTo("Casual");
        assertThat(saved.getFromDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(saved.getToDate()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(saved.getReason()).isEqualTo("Family function");
        assertThat(saved.getStatus()).isEqualTo(LeaveRequest.Status.Pending);

        // Pending, so nothing is spent yet — rule 25.
        assertThat(balances.remaining(me.getId(), "Casual", 2026)).isEqualTo(12);
        // And it is auditable like any other leave application.
        assertThat(auditRepo.findAll()).anySatisfy(a ->
            assertThat(a.getAction()).isEqualTo("LEAVE_APPLIED"));
    }

    @Test
    @DisplayName("the apply-leave tool is refused by the same quota rule as the form")
    @WithMockUser(username = "arjun@test.io", roles = "EMPLOYEE")
    void applyLeaveToolRespectsTheQuota() {
        Employee me = employee("Arjun", "Mehta", "arjun@test.io", "50000");
        balances.adjust(me.getId(), "Casual", 2026, 12);   // whole year spent

        HrTools.LeaveApplicationReceipt r =
            tools.applyForLeave("Casual", "2026-11-10", "2026-11-11", "trip");

        assertThat(r.ok()).isFalse();
        assertThat(r.error()).contains("Casual").contains("0");
        assertThat(leaveRepo.count()).isZero();
    }

    @Test
    @DisplayName("a malformed date is reported, not guessed at")
    @WithMockUser(username = "arjun@test.io", roles = "EMPLOYEE")
    void applyLeaveRejectsABadDate() {
        employee("Arjun", "Mehta", "arjun@test.io", "50000");

        // Filing leave against a guessed date is a real error in somebody's record.
        HrTools.LeaveApplicationReceipt r =
            tools.applyForLeave("Casual", "next monday", "2026-11-11", null);
        assertThat(r.ok()).isFalse();
        assertThat(r.error()).contains("yyyy-MM-dd");
        assertThat(leaveRepo.count()).isZero();

        assertThat(tools.applyForLeave("Casual", "2026-11-12", "2026-11-10", null).ok()).isFalse();
        assertThat(leaveRepo.count()).isZero();
    }

    // ── 3. an employee cannot reach another employee's data through a tool ───────

    @Test
    @DisplayName("the tools act on the signed-in employee even when a colleague's data exists")
    @WithMockUser(username = "arjun@test.io", roles = "EMPLOYEE")
    void toolsAreScopedToTheSignedInEmployee() {
        Employee me = employee("Arjun", "Mehta", "arjun@test.io", "50000");
        Employee colleague = employee("Priya", "Sharma", "priya@test.io", "99000");
        // Priya has spent nothing; Arjun has spent 5 days. A leak would show 12 remaining.
        balances.adjust(me.getId(), "Casual", LocalDate.now().getYear(), 5);

        assertThat(tools.getMyLeaveBalance("Casual", null).balances())
            .singleElement().satisfies(l -> assertThat(l.remaining()).isEqualTo(7));

        // There is no parameter on any tool that could redirect it at Priya. Filing goes
        // to Arjun, so a request can never appear in a colleague's name — the hole rule
        // 26 closed on /leave/apply cannot reopen through chat.
        var receipt = tools.applyForLeave("Sick", "2026-05-04", "2026-05-04", "flu");
        assertThat(receipt.ok()).isTrue();
        assertThat(leaveRepo.findById(receipt.requestId()).orElseThrow()
            .getEmployee().getId()).isEqualTo(me.getId());
        assertThat(leaveRepo.findByEmployeeId(colleague.getId())).isEmpty();
    }

    @Test
    @DisplayName("the directory tool shows your own salary and never a colleague's")
    @WithMockUser(username = "arjun@test.io", roles = "EMPLOYEE")
    void directoryToolMasksEverybodyElsesPay() {
        employee("Arjun", "Mehta", "arjun@test.io", "50000");
        employee("Priya", "Sharma", "priya@test.io", "99000");

        HrTools.EmployeeDirectoryResult mine = tools.findEmployee("arjun");
        assertThat(mine.ok()).isTrue();
        assertThat(mine.matches()).singleElement().satisfies(m -> {
            assertThat(m.isYou()).isTrue();
            assertThat(m.basicSalary()).isNotNull();
            assertThat(m.basicSalary().intValue()).isEqualTo(50000);
        });

        HrTools.EmployeeDirectoryResult theirs = tools.findEmployee("priya@test.io");
        assertThat(theirs.ok()).isTrue();
        assertThat(theirs.matches()).singleElement().satisfies(m -> {
            assertThat(m.isYou()).isFalse();
            // Directory facts are fine; pay is not directory information (rule 27).
            assertThat(m.fullName()).isEqualTo("Priya Sharma");
            assertThat(m.designation()).isEqualTo("Engineer");
            assertThat(m.basicSalary()).isNull();
        });
    }

    @Test
    @DisplayName("HR gets no extra reach through chat than they would as an employee")
    @WithMockUser(username = "hr@test.io", roles = "HR_ADMIN")
    void chatGrantsNoPrivilegeEscalation() {
        employee("Harini", "Rao", "hr@test.io", "80000");
        employee("Priya", "Sharma", "priya@test.io", "99000");

        // HR_ADMIN *can* read every salary over /api/employees. The assistant deliberately
        // does not: a chat transcript is a much easier place to leak pay from than a
        // table somebody had to navigate to.
        assertThat(tools.findEmployee("priya").matches())
            .singleElement().satisfies(m -> assertThat(m.basicSalary()).isNull());
    }

    @Test
    @DisplayName("a login with no employee record is told so, not answered with somebody else's data")
    @WithMockUser(username = "ghost@test.io", roles = "EMPLOYEE")
    void unlinkedLoginIsRefused() {
        employee("Arjun", "Mehta", "arjun@test.io", "50000");

        HrTools.LeaveBalanceReport r = tools.getMyLeaveBalance(null, null);
        assertThat(r.ok()).isFalse();
        assertThat(r.error()).contains("not linked to an employee record");
        assertThat(r.balances()).isEmpty();

        assertThat(tools.applyForLeave("Casual", "2026-11-10", "2026-11-10", null).ok()).isFalse();
        assertThat(leaveRepo.count()).isZero();
    }

    @Test
    @DisplayName("with no authenticated caller the tools refuse outright")
    void anonymousCallerIsRefused() {
        employee("Arjun", "Mehta", "arjun@test.io", "50000");

        assertThat(tools.getMyLeaveBalance(null, null).ok()).isFalse();
        assertThat(tools.applyForLeave("Casual", "2026-11-10", "2026-11-10", null).ok()).isFalse();
        assertThat(leaveRepo.count()).isZero();
    }

    // ── the endpoint, with the model mocked ──────────────────────────────────────

    @Test
    @DisplayName("/chat returns the model's reply and never calls a real API")
    @WithMockUser(username = "arjun@test.io", roles = "EMPLOYEE")
    void chatEndpointRelaysTheReply() throws Exception {
        employee("Arjun", "Mehta", "arjun@test.io", "50000");
        modelSays("You have 9 casual leaves left for 2026.");

        mvc.perform(post("/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"How many casual leaves do I have left?\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.reply").value("You have 9 casual leaves left for 2026."))
            // Always present so the panel can render unconditionally; empty for a data answer.
            .andExpect(jsonPath("$.data.sources").isArray())
            .andExpect(jsonPath("$.data.sources").isEmpty());
    }

    @Test
    @DisplayName("/chat tells the model today's date, so it can resolve \"next Monday\"")
    @WithMockUser(username = "arjun@test.io", roles = "EMPLOYEE")
    void chatSuppliesTodaysDate() throws Exception {
        employee("Arjun", "Mehta", "arjun@test.io", "50000");
        modelSays("ok");

        mvc.perform(post("/chat").contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"apply leave next monday\"}")).andExpect(status().isOk());

        ArgumentCaptor<Prompt> sent = ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(chatModel).call(sent.capture());
        assertThat(sent.getValue().getContents()).contains(LocalDate.now().toString());
    }

    @Test
    @DisplayName("the unavailable reason names the right fix for whichever model is selected")
    void unavailableReasonIsProviderSpecific() {
        // Pure logic, asserted without booting a second context or installing Ollama.
        // Getting this wrong means telling an Ollama user to set an API key they do not
        // need, or telling an Anthropic user to install software they do not need.
        String ollamaDown = AssistantProvider.reasonFor(
            "ollama", true, AssistantProvider.Probe.DOWN, "http://localhost:11434", "qwen2.5:7b");
        assertThat(ollamaDown)
            .contains("not responding")
            .contains("ollama.com")
            .contains("ollama pull qwen2.5:7b")
            .doesNotContain("ANTHROPIC_API_KEY");

        // Service up, model absent — the state during a multi-GB pull. Reporting this as
        // ready let the panel accept a question it could not possibly answer.
        String noModel = AssistantProvider.reasonFor(
            "ollama", true, AssistantProvider.Probe.NO_MODEL, "http://localhost:11434", "qwen2.5:7b");
        assertThat(noModel)
            .contains("Ollama is running")
            .contains("is not there yet")
            .contains("ollama pull qwen2.5:7b")
            .doesNotContain("ollama.com");          // it is already installed; don't resend them

        assertThat(AssistantProvider.reasonFor(
            "ollama", true, AssistantProvider.Probe.READY, "http://localhost:11434", "qwen2.5:7b")).isNull();

        String noKey = AssistantProvider.reasonFor(
            "anthropic", false, AssistantProvider.Probe.READY, "", "");
        assertThat(noKey).contains("ANTHROPIC_API_KEY").doesNotContain("Ollama");

        assertThat(AssistantProvider.reasonFor(
            "anthropic", true, AssistantProvider.Probe.READY, "", "")).isNull();
    }

    @Test
    @DisplayName("a pulled model is recognised however it was tagged")
    void modelPresenceDetection() {
        String tags = "{\"models\":[{\"name\":\"qwen2.5:7b\",\"size\":4700000000}]}";
        assertThat(AssistantProvider.hasModel(tags, "qwen2.5:7b")).isTrue();
        // A different size of the same family is not the configured model.
        assertThat(AssistantProvider.hasModel(tags, "qwen2.5:14b")).isFalse();
        assertThat(AssistantProvider.hasModel(tags, "llama3.1:8b")).isFalse();
        // An untagged pull is reported by Ollama as name:latest.
        assertThat(AssistantProvider.hasModel(
            "{\"models\":[{\"name\":\"qwen2.5:latest\"}]}", "qwen2.5")).isTrue();
        // Empty registry, mid-pull.
        assertThat(AssistantProvider.hasModel("{\"models\":[]}", "qwen2.5:7b")).isFalse();
        assertThat(AssistantProvider.hasModel(null, "qwen2.5:7b")).isFalse();
    }

    @Test
    @DisplayName("/chat/status tells the panel which model answers and whether it is ready")
    @WithMockUser(username = "arjun@test.io", roles = "EMPLOYEE")
    void statusReportsProviderAndReadiness() throws Exception {
        // The suite pins the Anthropic path with a fake key, so it reports ready.
        mvc.perform(get("/chat/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.provider").value("anthropic"))
            .andExpect(jsonPath("$.data.configured").value(true))
            .andExpect(jsonPath("$.data.ready").value(true))
            .andExpect(jsonPath("$.data.reason").doesNotExist());
    }

    @Test
    @DisplayName("/chat needs a token like every other endpoint")
    void chatRequiresAuthentication() throws Exception {
        // 401 rather than 403: api.js clears the session on 401 only.
        mvc.perform(post("/chat").contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"hello\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/chat/status")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an empty question is rejected before the model is called")
    @WithMockUser(username = "arjun@test.io", roles = "EMPLOYEE")
    void blankQuestionIsRejected() throws Exception {
        mvc.perform(post("/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"   \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
        org.mockito.Mockito.verifyNoInteractions(chatModel);
    }

    // ── policy RAG and citations ─────────────────────────────────────────────────

    @Test
    @DisplayName("an indexed policy PDF is retrieved and cited with its document, section and page")
    @WithMockUser(username = "hr@test.io", roles = "HR_ADMIN")
    void policyAnswerCarriesItsCitation() throws Exception {
        employee("Harini", "Rao", "hr@test.io", "80000");
        policies.ingest(policyPdf(), "Leave Policy 2026");
        modelSays("According to the Leave Policy 2026, section 4. Notice Period, "
            + "the notice period is 60 days.");

        MvcResult res = mvc.perform(post("/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"What is the notice period?\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sources").isNotEmpty())
            .andReturn();

        var sources = json.readTree(res.getResponse().getContentAsString()).path("data").path("sources");
        assertThat(sources.get(0).path("document").asText()).isEqualTo("Leave Policy 2026");
        assertThat(sources.get(0).path("fileName").asText()).isEqualTo("leave-policy.pdf");
        assertThat(sources.get(0).path("page").asInt()).isEqualTo(1);
        // A numbered section beats the handbook's banner line as a locator.
        assertThat(sources.get(0).path("section").asText()).startsWith("4. Notice Period");
        // The excerpt lets an operator confirm the citation without opening the PDF.
        assertThat(sources.get(0).path("excerpt").asText()).contains("notice period of 60 days");

        // The retrieved text really was put in front of the model, rather than the
        // citation being decoration bolted onto an ungrounded answer.
        ArgumentCaptor<Prompt> sent = ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(chatModel).call(sent.capture());
        assertThat(sent.getValue().getContents())
            .contains("POLICY EXTRACTS")
            .contains("60 days")
            .contains("Leave Policy 2026");
    }

    @Test
    @DisplayName("with no policy documents the model is told to say so rather than guess")
    @WithMockUser(username = "arjun@test.io", roles = "EMPLOYEE")
    void withoutPoliciesTheModelIsToldNotToGuess() throws Exception {
        employee("Arjun", "Mehta", "arjun@test.io", "50000");
        modelSays("No policy documents have been uploaded yet.");

        mvc.perform(post("/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"What is the notice period?\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sources").isEmpty());

        ArgumentCaptor<Prompt> sent = ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(chatModel).call(sent.capture());
        assertThat(sent.getValue().getContents()).contains("no policy documents have been uploaded");
    }

    @Test
    @DisplayName("only HR and the owner may publish a policy document")
    @WithMockUser(username = "arjun@test.io", roles = "EMPLOYEE")
    void policyUploadIsHrOnly() throws Exception {
        // Whatever is indexed becomes the assistant's answer for everybody who asks.
        mvc.perform(multipart("/chat/policies").file(policyPdf()))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/chat/policies/leave-policy.pdf"))
            .andExpect(status().isForbidden());
        // Reading the list is open, so the panel can name its sources.
        mvc.perform(get("/chat/policies")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a non-PDF upload is refused with something the operator can act on")
    @WithMockUser(username = "hr@test.io", roles = "HR_ADMIN")
    void nonPdfIsRefused() {
        assertThatThrownBy(() -> policies.ingest(
                new MockMultipartFile("file", "handbook.docx", "application/msword", "nope".getBytes()), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a PDF");

        assertThatThrownBy(() -> policies.ingest(
                new MockMultipartFile("file", "fake.pdf", "application/pdf", "not really a pdf".getBytes()), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a valid PDF");
    }

    @Test
    @DisplayName("re-uploading a policy replaces it instead of indexing it twice")
    @WithMockUser(username = "hr@test.io", roles = "HR_ADMIN")
    void reuploadReplaces() {
        policies.ingest(policyPdf(), "Leave Policy 2026");
        policies.ingest(policyPdf(), "Leave Policy 2027");

        assertThat(policies.list()).singleElement()
            .satisfies(d -> assertThat(d.title()).isEqualTo("Leave Policy 2027"));
    }

    @Test
    @DisplayName("the test suite writes its index to a temp directory, never to ~/.axvon")
    void indexNeverTouchesTheRealInstall() {
        // A regression here would have the suite overwrite a customer's real policy index.
        assertThat(aiPaths.directory().toString())
            .doesNotContain(".axvon")
            .contains("axvon-ai-test");
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    /** A one-page policy PDF built with PDFBox, so the RAG path runs on a real document. */
    private MockMultipartFile policyPdf() {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.setLeading(16f);
                cs.newLineAtOffset(60, 720);
                for (String line : List.of(
                        "AXVON POLICY HANDBOOK",
                        "",
                        "4. Notice Period",
                        "An employee resigning from the company shall serve a notice",
                        "period of 60 days from the date the resignation is accepted.",
                        "The notice period may be waived only with written approval",
                        "from the head of Human Resources.",
                        "",
                        "5. Probation",
                        "New joiners serve a probation period of six months.")) {
                    cs.showText(line);
                    cs.newLine();
                }
                cs.endText();
            }
            doc.save(out);
            return new MockMultipartFile("file", "leave-policy.pdf", "application/pdf", out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("could not build the test PDF", e);
        }
    }
}
