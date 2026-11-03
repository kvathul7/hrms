package com.axvon.hrms.ai;

import com.axvon.hrms.ai.HrAssistantService.AssistantUnavailableException;
import com.axvon.hrms.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

// ── AXVON HR ASSISTANT ───────────────────────────────────────────────
// Reachable only with a valid token: SecurityConfig ends in
// anyRequest().authenticated(), and /chat is not on the permitAll list. That matters from
// the very first step — the assistant will shortly be able to read HR records, and the
// employee it answers for is taken from the token, never from the chat message.
@RestController @RequestMapping("/chat") @RequiredArgsConstructor @Slf4j
class ChatController {

    /** A question longer than this is a paste accident, not a question. */
    private static final int MAX_QUESTION = 2000;

    private final HrAssistantService assistant;

    /** Lets the frontend hide the chat panel instead of offering a button that fails. */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String,Object>>> status(){
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("configured",assistant.isConfigured());
        m.put("name","AXVON HR Assistant");
        return ResponseEntity.ok(ApiResponse.ok(m));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String,Object>>> ask(@RequestBody Map<String,Object> body){
        Object raw=body.get("message");
        String question=raw==null?"":raw.toString().trim();
        if(question.isEmpty())
            return ResponseEntity.badRequest().body(ApiResponse.error("Type a question first."));
        if(question.length()>MAX_QUESTION)
            return ResponseEntity.badRequest().body(ApiResponse.error(
                "That question is too long ("+question.length()+" characters, limit "+MAX_QUESTION+")."));
        try{
            HrAssistantService.Answer a=assistant.ask(question);
            Map<String,Object> m=new LinkedHashMap<>();
            m.put("reply",a.reply());
            // Always present, empty for a data or action answer. The panel renders these
            // under the reply so a policy claim can be traced to a page of a real PDF.
            m.put("sources",a.sources());
            return ResponseEntity.ok(ApiResponse.ok(m));
        }catch(AssistantUnavailableException e){
            // 200 with success=false, not a 5xx. api.js treats a 401 as "session gone" and
            // a 403 as a role denial; neither is true here, and the person should simply
            // see why the assistant cannot answer.
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }
}
