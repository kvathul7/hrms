package com.axvon.hrms.ai;

import com.axvon.hrms.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ── POLICY DOCUMENTS (HR Assistant knowledge base) ───────────────────
// Upload and removal are SUPER_ADMIN / HR_ADMIN only: whatever is indexed here becomes
// the assistant's answer about company policy for every employee who asks, so it is an
// HR publication, not a personal file. Listing is open to any signed-in user so the chat
// panel can tell people which documents the answers come from.
@RestController @RequestMapping("/chat/policies") @RequiredArgsConstructor @Slf4j
class PolicyController {

    private final PolicyLibraryService policies;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PolicyLibraryService.PolicyDocument>>> list(){
        return ResponseEntity.ok(ApiResponse.ok(policies.list()));
    }

    @PostMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String,Object>>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value="title",required=false) String title){
        // Chunking and embedding a long PDF is slow — a few seconds per hundred pages on
        // the local ONNX model — so this is deliberately synchronous: the operator should
        // see the result, not a job id.
        var doc=policies.ingest(file,title);
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("document",doc);
        m.put("message","\""+doc.title()+"\" is indexed — "+doc.chunks()
            +" passages across "+doc.pages()+" pages. The assistant can now answer from it.");
        return ResponseEntity.ok(ApiResponse.ok("Indexed",m));
    }

    @DeleteMapping("/{fileName}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String fileName){
        if(!policies.delete(fileName))
            return ResponseEntity.ok(ApiResponse.error("No indexed policy document called \""+fileName+"\"."));
        return ResponseEntity.ok(ApiResponse.ok("Removed",null));
    }
}
