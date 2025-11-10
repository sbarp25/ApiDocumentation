package com.apidoc.apidocumentation.doc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class ApiDocFileService {
    
    @Value("${apidoc.doc.directory:api-docs}")
    private String docDirectory;
    
    @Value("${apidoc.doc.filename:api-documentation.json}")
    private String docFileName;
    
    private final ObjectMapper objectMapper;
    
    public ApiDocFileService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }
    
    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(docDirectory));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create doc directory", e);
        }
    }
    
    public void saveDocumentation(Map<String, ApiEndpointInfo> endpoints) {
        try {
            Path filePath = Paths.get(docDirectory, docFileName);
            
            Map<String, Object> docData = Map.of(
                "generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                "totalEndpoints", endpoints.size(),
                "endpoints", endpoints
            );
            
            objectMapper.writeValue(filePath.toFile(), docData);
            
            // Also save as HTML for easy viewing
            saveAsHtml(endpoints);
            
        } catch (IOException e) {
            System.err.println("Failed to save API documentation: " + e.getMessage());
        }
    }
    
    private void saveAsHtml(Map<String, ApiEndpointInfo> endpoints) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html>\n<head>\n");
        html.append("<title>API Documentation</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }\n");
        html.append(".endpoint { background: white; padding: 20px; margin: 10px 0; border-radius: 5px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
        html.append(".method { display: inline-block; padding: 5px 10px; border-radius: 3px; color: white; font-weight: bold; margin-right: 10px; }\n");
        html.append(".GET { background: #61affe; }\n");
        html.append(".POST { background: #49cc90; }\n");
        html.append(".PUT { background: #fca130; }\n");
        html.append(".DELETE { background: #f93e3e; }\n");
        html.append(".param { background: #f0f0f0; padding: 10px; margin: 5px 0; border-radius: 3px; }\n");
        html.append("h1 { color: #333; }\n");
        html.append(".tag { background: #e3f2fd; padding: 3px 8px; border-radius: 3px; margin: 0 5px; font-size: 12px; }\n");
        html.append("</style>\n</head>\n<body>\n");
        html.append("<h1>API Documentation</h1>\n");
        html.append("<p>Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)).append("</p>\n");
        html.append("<p>Total Endpoints: ").append(endpoints.size()).append("</p>\n");
        
        endpoints.values().forEach(endpoint -> {
            html.append("<div class='endpoint'>\n");
            html.append("<div><span class='method ").append(endpoint.getMethod()).append("'>")
                .append(endpoint.getMethod()).append("</span>");
            html.append("<strong>").append(endpoint.getPath()).append("</strong></div>\n");
            
            if (endpoint.getDescription() != null && !endpoint.getDescription().isEmpty()) {
                html.append("<p>").append(endpoint.getDescription()).append("</p>\n");
            }
            
            if (endpoint.getTags() != null && !endpoint.getTags().isEmpty()) {
                html.append("<div>Tags: ");
                endpoint.getTags().forEach(tag -> 
                    html.append("<span class='tag'>").append(tag).append("</span>"));
                html.append("</div>\n");
            }
            
            if (endpoint.getParameters() != null && !endpoint.getParameters().isEmpty()) {
                html.append("<h4>Parameters:</h4>\n");
                endpoint.getParameters().forEach(param -> {
                    html.append("<div class='param'>\n");
                    html.append("<strong>").append(param.getName()).append("</strong> ");
                    html.append("(").append(param.getType()).append(") - ");
                    html.append(param.getLocation()).append(" ");
                    html.append(param.isRequired() ? "<span style='color:red;'>*required</span>" : "optional");
                    if (param.getDescription() != null && !param.getDescription().isEmpty()) {
                        html.append("<br/>").append(param.getDescription());
                    }
                    html.append("</div>\n");
                });
            }
            
            html.append("</div>\n");
        });
        
        html.append("</body>\n</html>");
        
        Path htmlPath = Paths.get(docDirectory, "api-documentation.html");
        Files.writeString(htmlPath, html.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
