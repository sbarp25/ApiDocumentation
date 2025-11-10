package com.apidoc.apidocumentation.doc;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api-docs")
@RequiredArgsConstructor
public class ApiDocController {

    //    private final ApiDocumentationService docService;
    private final ApiLogFileService logService;
    @Autowired
    private  CompleteDocumentationService completeDocumentationService;


//    @GetMapping("/endpoints")
//    public Map<String, ApiEndpointInfo> getAllEndpoints() {
//        return docService.getAllEndpoints();
//    }

    @GetMapping("/logs")
    public List<ApiLog> getAllLogs() {
        return logService.getAllLogs();
    }

    @GetMapping("/logs/endpoint")
    public List<ApiLog> getLogsByEndpoint(@RequestParam String endpoint) {
        return logService.getLogsByEndpoint(endpoint);
    }

    @GetMapping("/logs/date")
    public List<ApiLog> getLogsByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return logService.getLogsByDate(date);
    }

    @DeleteMapping("/logs/clean")
    public String cleanOldLogs(@RequestParam(defaultValue = "30") int daysToKeep) {
        logService.cleanOldLogs(daysToKeep);
        return "Old logs cleaned successfully";
    }
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateCompleteDocumentation() {
        try {
            Map<String, Object> documentation = completeDocumentationService.generateCompleteDocumentation();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Documentation generated successfully",
                    "files", Arrays.asList(
                            "complete-api-documentation.json",
                            "API-DOCUMENTATION.md",
                            "API-DOCUMENTATION.html",
                            "postman-collection.json"
                    ),
                    "documentation", documentation
            ));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to generate documentation: " + e.getMessage()
            ));
        }
    }
}
