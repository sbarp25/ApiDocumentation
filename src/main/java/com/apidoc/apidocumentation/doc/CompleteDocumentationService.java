package com.apidoc.apidocumentation.doc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.ServletContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CompleteDocumentationService {
    
    private final ApiDocumentationService apiDocService;
    private final ServerProperties serverProperties;
    private final ServletContext servletContext;
    
    @Value("${server.port:8080}")
    private String serverPort;
    
    @Value("${server.servlet.context-path:}")
    private String contextPath;
    
    @Value("${apidoc.doc.directory:api-docs}")
    private String docDirectory;
    
    @Value("${spring.application.name:Application}")
    private String applicationName;
    
    @Value("${apidoc.doc.version:1.0.0}")
    private String apiVersion;
    
    @Value("${apidoc.doc.description:API Documentation}")
    private String apiDescription;
    
    private final ObjectMapper objectMapper;
    @Value("${apidoc.log.directory:api-logs}")
    String logDirectory;
    
    public CompleteDocumentationService(ApiDocumentationService apiDocService,
                                       ServerProperties serverProperties,
                                       ServletContext servletContext) {
        this.apiDocService = apiDocService;
        this.serverProperties = serverProperties;
        this.servletContext = servletContext;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    public Map<String, Object> generateCompleteDocumentation() throws IOException {
        Map<String, Object> documentation = new LinkedHashMap<>();
        
        // Application Information
        Map<String, Object> appInfo = new LinkedHashMap<>();
        appInfo.put("name", applicationName);
        appInfo.put("version", apiVersion);
        appInfo.put("description", apiDescription);
        appInfo.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        documentation.put("application", appInfo);
        
        // Server Information
        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("protocol", "http");
        serverInfo.put("host", "localhost");
        serverInfo.put("port", serverPort);
        serverInfo.put("contextPath", contextPath.isEmpty() ? "/" : contextPath);
        serverInfo.put("baseUrl", buildBaseUrl());
        documentation.put("server", serverInfo);
        
        // API Endpoints
        Map<String, ApiEndpointInfo> endpoints = apiDocService.getAllEndpoints();
        // load smaple body if presnet
        loadSamplesFromLogs(endpoints);
        
        Map<String, Object> apiInfo = new LinkedHashMap<>();
        apiInfo.put("totalEndpoints", endpoints.size());
        apiInfo.put("endpoints", formatEndpoints(endpoints));
        documentation.put("api", apiInfo);

        // Save all formats
        saveAsJson(documentation);
        saveAsMarkdown(documentation, endpoints);
        saveAsHtml(documentation, endpoints);
        saveAsPostmanCollection(documentation, endpoints);
        
        return documentation;
    }
    
    private String buildBaseUrl() {
        String ctx = contextPath.isEmpty() ? "" : contextPath;
        return "http://localhost:" + serverPort + ctx;
    }
    
    private List<Map<String, Object>> formatEndpoints(Map<String, ApiEndpointInfo> endpoints) {
        return endpoints.values().stream()
            .sorted(Comparator.comparing(ApiEndpointInfo::getPath))
            .map(this::formatEndpoint)
            .collect(Collectors.toList());
    }
    
    private Map<String, Object> formatEndpoint(ApiEndpointInfo endpoint) {
        Map<String, Object> formatted = new LinkedHashMap<>();
        formatted.put("method", endpoint.getMethod());
        formatted.put("path", endpoint.getPath());
        formatted.put("fullUrl", buildBaseUrl() + endpoint.getPath());
        formatted.put("description", endpoint.getDescription());
        formatted.put("tags", endpoint.getTags());
        
        if (endpoint.getParameters() != null && !endpoint.getParameters().isEmpty()) {
            formatted.put("parameters", endpoint.getParameters());
        }
        
        return formatted;
    }
    
    private void saveAsJson(Map<String, Object> documentation) throws IOException {
        Path filePath = Paths.get(docDirectory, "complete-api-documentation.json");
        objectMapper.writeValue(filePath.toFile(), documentation);
    }

    private void saveAsMarkdown(Map<String, Object> documentation,
                                Map<String, ApiEndpointInfo> endpoints) throws IOException {
        StringBuilder md = new StringBuilder();

        // Title and Overview
        md.append("# ").append(applicationName).append(" - API Documentation\n\n")
                .append("**Version:** ").append(apiVersion).append("\n\n")
                .append("**Generated:** ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)).append("\n\n")
                .append("**Description:** ").append(apiDescription).append("\n\n");

        // Server Information
        md.append("## Server Information\n\n")
                .append("- **Base URL:** `").append(buildBaseUrl()).append("`\n")
                .append("- **Port:** ").append(serverPort).append("\n")
                .append("- **Context Path:** ").append(contextPath.isEmpty() ? "/" : contextPath).append("\n\n");

        // Table of Contents
        md.append("## Table of Contents\n\n");
        Map<String, List<ApiEndpointInfo>> groupedByTag = groupEndpointsByTag(endpoints);
        groupedByTag.keySet().forEach(tag ->
                md.append("- [").append(tag).append("](#").append(tag.toLowerCase().replace(" ", "-")).append(")\n"));
        md.append("\n---\n\n");

        // Endpoints by Tag
        groupedByTag.forEach((tag, tagEndpoints) -> {
            md.append("## ").append(tag).append("\n\n");

            tagEndpoints.forEach(endpoint -> {
                md.append("### ").append(endpoint.getMethod()).append(" ").append(endpoint.getPath()).append("\n\n");

                if (endpoint.getDescription() != null && !endpoint.getDescription().isEmpty()) {
                    md.append("**Description:** ").append(endpoint.getDescription()).append("\n\n");
                }

                md.append("**Full URL:** `").append(buildBaseUrl()).append(endpoint.getPath()).append("`\n\n");

                // Sample Request
                if (endpoint.getApiLog() != null && !"GET".equalsIgnoreCase(endpoint.getMethod())) {
                    md.append("**Sample Request:**\n```json\n");
                    try {
                        Object body = endpoint.getApiLog().getRequestBody();
                        if (body instanceof String str && str.trim().startsWith("{")) {
                            body = objectMapper.readValue(str, Object.class);
                        }
                        md.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    md.append("\n```\n\n");
                }

                // Sample Headers
                if (endpoint.getApiLog() != null && endpoint.getApiLog().getRequestHeaders() != null) {
                    md.append("**Sample Headers:**\n```json\n");
                    try {
                        md.append(objectMapper.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(endpoint.getApiLog().getRequestHeaders()));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    md.append("\n```\n\n");
                }

                // Sample Response
                if (endpoint.getApiLog() != null && endpoint.getApiLog().getResponseBody() != null) {
                    md.append("**Sample Response:**\n```json\n");
                    try {
                        md.append(objectMapper.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(endpoint.getApiLog().getResponseBody()));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    md.append("\n```\n\n");
                }

                // Parameters
                if (endpoint.getParameters() != null && !endpoint.getParameters().isEmpty()) {
                    md.append("**Parameters:**\n\n")
                            .append("| Name | Type | Location | Required | Description |\n")
                            .append("|------|------|----------|----------|-------------|\n");

                    endpoint.getParameters().forEach(param -> md.append("| ")
                            .append(param.getName()).append(" | ")
                            .append(param.getType()).append(" | ")
                            .append(param.getLocation()).append(" | ")
                            .append(param.isRequired() ? "✓" : "✗").append(" | ")
                            .append(param.getDescription() != null ? param.getDescription() : "-").append(" |\n"));
                    md.append("\n");
                }

                // cURL Example
                md.append("**cURL Example:**\n```bash\n")
                        .append(generateCurlExample(endpoint))
                        .append("\n```\n\n---\n\n");
            });
        });

        Path mdPath = Paths.get(docDirectory, "API-DOCUMENTATION.md");
        Files.writeString(mdPath, md.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }


    private void saveAsHtml(Map<String, Object> documentation, 
                           Map<String, ApiEndpointInfo> endpoints) throws IOException {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>").append(applicationName).append(" - API Documentation</title>\n");
        html.append("    <style>\n");
        html.append("        * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        html.append("        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f5f7fa; color: #333; line-height: 1.6; }\n");
        html.append("        .container { max-width: 1200px; margin: 0 auto; padding: 20px; }\n");
        html.append("        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 40px; border-radius: 10px; margin-bottom: 30px; box-shadow: 0 10px 30px rgba(0,0,0,0.1); }\n");
        html.append("        .header h1 { font-size: 2.5em; margin-bottom: 10px; }\n");
        html.append("        .header .version { font-size: 1.2em; opacity: 0.9; }\n");
        html.append("        .server-info { background: white; padding: 25px; border-radius: 10px; margin-bottom: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.05); }\n");
        html.append("        .server-info h2 { color: #667eea; margin-bottom: 15px; }\n");
        html.append("        .server-info .info-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 15px; }\n");
        html.append("        .info-item { padding: 10px; background: #f8f9fa; border-radius: 5px; }\n");
        html.append("        .info-item label { font-weight: bold; color: #666; display: block; margin-bottom: 5px; }\n");
        html.append("        .info-item code { background: #e9ecef; padding: 5px 10px; border-radius: 3px; display: inline-block; }\n");
        html.append("        .endpoint { background: white; padding: 25px; margin-bottom: 20px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.05); border-left: 4px solid #667eea; }\n");
        html.append("        .endpoint-header { display: flex; align-items: center; margin-bottom: 15px; }\n");
        html.append("        .method { display: inline-block; padding: 8px 15px; border-radius: 5px; color: white; font-weight: bold; margin-right: 15px; font-size: 0.9em; }\n");
        html.append("        .GET { background: #61affe; }\n");
        html.append("        .POST { background: #49cc90; }\n");
        html.append("        .PUT { background: #fca130; }\n");
        html.append("        .DELETE { background: #f93e3e; }\n");
        html.append("        .PATCH { background: #50e3c2; }\n");
        html.append("        .endpoint-path { font-size: 1.3em; font-weight: 600; color: #2c3e50; }\n");
        html.append("        .endpoint-url { background: #f8f9fa; padding: 10px 15px; border-radius: 5px; font-family: 'Courier New', monospace; font-size: 0.9em; margin: 10px 0; word-break: break-all; }\n");
        html.append("        .description { color: #666; margin: 15px 0; }\n");
        html.append("        .tags { margin: 10px 0; }\n");
        html.append("        .tag { background: #e3f2fd; color: #1976d2; padding: 5px 12px; border-radius: 15px; margin-right: 8px; font-size: 0.85em; display: inline-block; }\n");
        html.append("        .parameters { margin-top: 20px; }\n");
        html.append("        .parameters h4 { color: #667eea; margin-bottom: 10px; }\n");
        html.append("        .param-table { width: 100%; border-collapse: collapse; margin-top: 10px; }\n");
        html.append("        .param-table th { background: #f8f9fa; padding: 12px; text-align: left; font-weight: 600; border-bottom: 2px solid #dee2e6; }\n");
        html.append("        .param-table td { padding: 12px; border-bottom: 1px solid #dee2e6; }\n");
        html.append("        .param-table tr:hover { background: #f8f9fa; }\n");
        html.append("        .required { color: #f93e3e; font-weight: bold; }\n");
        html.append("        .curl-section { margin-top: 20px; }\n");
        html.append("        .curl-section h4 { color: #667eea; margin-bottom: 10px; }\n");
        html.append("        .curl-code { background: #2d2d2d; color: #f8f8f2; padding: 15px; border-radius: 5px; font-family: 'Courier New', monospace; font-size: 0.9em; overflow-x: auto; }\n");
        html.append("        .section-title { color: #667eea; font-size: 2em; margin: 40px 0 20px 0; padding-bottom: 10px; border-bottom: 3px solid #667eea; }\n");
        html.append("        .toc { background: white; padding: 25px; border-radius: 10px; margin-bottom: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.05); }\n");
        html.append("        .toc h2 { color: #667eea; margin-bottom: 15px; }\n");
        html.append("        .toc ul { list-style: none; }\n");
        html.append("        .toc li { padding: 8px 0; }\n");
        html.append("        .toc a { color: #667eea; text-decoration: none; transition: all 0.3s; }\n");
        html.append("        .toc a:hover { color: #764ba2; padding-left: 10px; }\n");
        html.append("    </style>\n");
        html.append("</head>\n<body>\n");
        html.append("    <div class=\"container\">\n");
        
        // Header
        html.append("        <div class=\"header\">\n");
        html.append("            <h1>").append(applicationName).append("</h1>\n");
        html.append("            <div class=\"version\">Version: ").append(apiVersion).append("</div>\n");
        html.append("            <div class=\"version\">").append(apiDescription).append("</div>\n");
        html.append("            <div class=\"version\">Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</div>\n");
        html.append("        </div>\n");
        
        // Server Info
        html.append("        <div class=\"server-info\">\n");
        html.append("            <h2>Server Information</h2>\n");
        html.append("            <div class=\"info-grid\">\n");
        html.append("                <div class=\"info-item\"><label>Base URL</label><code>").append(buildBaseUrl()).append("</code></div>\n");
        html.append("                <div class=\"info-item\"><label>Port</label><code>").append(serverPort).append("</code></div>\n");
        html.append("                <div class=\"info-item\"><label>Context Path</label><code>").append(contextPath.isEmpty() ? "/" : contextPath).append("</code></div>\n");
        html.append("                <div class=\"info-item\"><label>Total Endpoints</label><code>").append(endpoints.size()).append("</code></div>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");
        
        // Table of Contents
        Map<String, List<ApiEndpointInfo>> groupedByTag = groupEndpointsByTag(endpoints);
        html.append("        <div class=\"toc\">\n");
        html.append("            <h2>Table of Contents</h2>\n");
        html.append("            <ul>\n");
        groupedByTag.keySet().forEach(tag -> 
            html.append("                <li><a href=\"#").append(tag.toLowerCase().replace(" ", "-")).append("\">").append(tag).append("</a></li>\n"));
        html.append("            </ul>\n");
        html.append("        </div>\n");
        
        // Endpoints
        groupedByTag.forEach((tag, tagEndpoints) -> {
            html.append("        <h2 class=\"section-title\" id=\"").append(tag.toLowerCase().replace(" ", "-")).append("\">").append(tag).append("</h2>\n");
            
            tagEndpoints.forEach(endpoint -> {
                html.append("        <div class=\"endpoint\">\n");
                html.append("            <div class=\"endpoint-header\">\n");
                html.append("                <span class=\"method ").append(endpoint.getMethod()).append("\">").append(endpoint.getMethod()).append("</span>\n");
                html.append("                <span class=\"endpoint-path\">").append(endpoint.getPath()).append("</span>\n");
                html.append("            </div>\n");
                
                if (endpoint.getDescription() != null && !endpoint.getDescription().isEmpty()) {
                    html.append("            <div class=\"description\">").append(endpoint.getDescription()).append("</div>\n");
                }
                
                html.append("            <div class=\"endpoint-url\">").append(buildBaseUrl()).append(endpoint.getPath()).append("</div>\n");
                
                if (endpoint.getTags() != null && !endpoint.getTags().isEmpty()) {
                    html.append("            <div class=\"tags\">\n");
                    endpoint.getTags().forEach(t -> 
                        html.append("                <span class=\"tag\">").append(t).append("</span>\n"));
                    html.append("            </div>\n");
                }
                
                if (endpoint.getParameters() != null && !endpoint.getParameters().isEmpty()) {
                    html.append("            <div class=\"parameters\">\n");
                    html.append("                <h4>Parameters</h4>\n");
                    html.append("                <table class=\"param-table\">\n");
                    html.append("                    <thead><tr><th>Name</th><th>Type</th><th>Location</th><th>Required</th><th>Description</th></tr></thead>\n");
                    html.append("                    <tbody>\n");
                    
                    endpoint.getParameters().forEach(param -> {
                        html.append("                        <tr>\n");
                        html.append("                            <td><strong>").append(param.getName()).append("</strong></td>\n");
                        html.append("                            <td>").append(param.getType()).append("</td>\n");
                        html.append("                            <td>").append(param.getLocation()).append("</td>\n");
                        html.append("                            <td>").append(param.isRequired() ? "<span class=\"required\">Yes</span>" : "No").append("</td>\n");
                        html.append("                            <td>").append(param.getDescription() != null ? param.getDescription() : "-").append("</td>\n");
                        html.append("                        </tr>\n");
                    });
                    
                    html.append("                    </tbody>\n");
                    html.append("                </table>\n");
                    html.append("            </div>\n");
                }
                
                html.append("            <div class=\"curl-section\">\n");
                html.append("                <h4>cURL Example</h4>\n");
                html.append("                <div class=\"curl-code\">").append(generateCurlExample(endpoint)).append("</div>\n");
                html.append("            </div>\n");

                // Add samples section
                if (endpoint.getApiLog() != null || endpoint.getHeaders() != null || endpoint.getResponseBody() != null) {
                    html.append("            <div class=\"samples\">\n");

                    if (endpoint.getApiLog() != null) {
                        html.append("                <div class=\"sample-section\">\n");
                        html.append("                    <h4>Sample Request</h4>\n");
                        try {
                            html.append("                    <pre class=\"sample-code\">").append(objectMapper.writerWithDefaultPrettyPrinter()
                                    .writeValueAsString(endpoint.getApiLog().getRequestBody())).append("</pre>\n");
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                        html.append("                </div>\n");
                    }

                    if (endpoint.getApiLog()!= null) {
                        html.append("                <div class=\"sample-section\">\n");
                        html.append("                    <h4>Sample Headers</h4>\n");
                        try {
                            html.append("                    <pre class=\"sample-code\">").append(objectMapper.writerWithDefaultPrettyPrinter()
                                    .writeValueAsString(endpoint.getApiLog().getRequestHeaders())).append("</pre>\n");
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                        html.append("                </div>\n");
                    }

                    if (endpoint.getApiLog()!= null) {
                        html.append("                <div class=\"sample-section\">\n");
                        html.append("                    <h4>Sample Response</h4>\n");
                        try {
                            html.append("                    <pre class=\"sample-code\">").append(objectMapper.writerWithDefaultPrettyPrinter()
                                    .writeValueAsString(endpoint.getApiLog().getResponseBody())).append("</pre>\n");
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                        html.append("                </div>\n");
                    }

                    html.append("            </div>\n");
                }

                
                html.append("        </div>\n");
            });
        });
        
        html.append("    </div>\n");
        html.append("</body>\n</html>");
        
        Path htmlPath = Paths.get(docDirectory, "API-DOCUMENTATION.html");
        Files.writeString(htmlPath, html.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
    
    private void saveAsPostmanCollection(Map<String, Object> documentation,
                                        Map<String, ApiEndpointInfo> endpoints) throws IOException {
        Map<String, Object> collection = new LinkedHashMap<>();
        
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", applicationName + " API");
        info.put("description", apiDescription);
        info.put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        collection.put("info", info);
        
        List<Map<String, Object>> items = new ArrayList<>();
        
        endpoints.values().forEach(endpoint -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", endpoint.getMethod() + " " + endpoint.getPath());
            item.put("request", createPostmanRequest(endpoint));
            items.add(item);
        });
        
        collection.put("item", items);
        
        Path postmanPath = Paths.get(docDirectory, "postman-collection.json");
        objectMapper.writeValue(postmanPath.toFile(), collection);
    }
    
    private Map<String, Object> createPostmanRequest(ApiEndpointInfo endpoint) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", endpoint.getMethod());
        
        Map<String, Object> url = new LinkedHashMap<>();
        url.put("raw", buildBaseUrl() + endpoint.getPath());
        url.put("protocol", "http");
        url.put("host", Arrays.asList("localhost"));
        url.put("port", serverPort);
        
        String[] pathParts = (contextPath + endpoint.getPath()).split("/");
        url.put("path", Arrays.stream(pathParts).filter(p -> !p.isEmpty()).collect(Collectors.toList()));
        
        request.put("url", url);
        request.put("description", endpoint.getDescription());
        
        return request;
    }
    
    private Map<String, List<ApiEndpointInfo>> groupEndpointsByTag(Map<String, ApiEndpointInfo> endpoints) {
        Map<String, List<ApiEndpointInfo>> grouped = new LinkedHashMap<>();
        
        endpoints.values().forEach(endpoint -> {
            List<String> tags = endpoint.getTags();
            if (tags == null || tags.isEmpty()) {
                grouped.computeIfAbsent("Uncategorized", k -> new ArrayList<>()).add(endpoint);
            } else {
                tags.forEach(tag -> 
                    grouped.computeIfAbsent(tag, k -> new ArrayList<>()).add(endpoint));
            }
        });
        
        return grouped;
    }

    private String generateCurlExample(ApiEndpointInfo endpoint) {
        StringBuilder curl = new StringBuilder();
        curl.append("curl -X ").append(endpoint.getMethod()).append(" \\\n");

        String url = buildBaseUrl() + endpoint.getPath();

        // Replace path variables
        if (endpoint.getApiLog() != null && endpoint.getApiLog().getPathVariables() != null) {
            for (Map.Entry<String, String> entry : endpoint.getApiLog().getPathVariables().entrySet()) {
                url = url.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        } else if (endpoint.getParameters() != null) {
            for (ParamInfo param : endpoint.getParameters()) {
                if ("path".equalsIgnoreCase(param.getLocation())) {
                    url = url.replace("{" + param.getName() + "}",
                            param.getExample() != null && !param.getExample().isEmpty() ? param.getExample() : "1");
                }
            }
        }

        // Add query params if present
        if (endpoint.getApiLog() != null && endpoint.getApiLog().getQueryParams() != null
                && !endpoint.getApiLog().getQueryParams().isEmpty()) {
            String queryString = endpoint.getApiLog().getQueryParams().entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("&"));
            url += "?" + queryString;
        }

        curl.append("  \"").append(url).append("\" \\\n");

        // Add base headers
        curl.append("  -H \"Content-Type: application/json\" \\\n");
        curl.append("  -H \"Accept: application/json\"");

        // Add custom headers
        if (endpoint.getApiLog() != null && endpoint.getApiLog().getRequestHeaders() != null) {
            for (Map.Entry<String, String> header : endpoint.getApiLog().getRequestHeaders().entrySet()) {
                String key = header.getKey();
                String value = String.valueOf(header.getValue());
                if (!key.equalsIgnoreCase("content-type") && !key.equalsIgnoreCase("accept")) {
                    curl.append(" \\\n  -H \"").append(key).append(": ").append(value).append("\"");
                }
            }
        }

        // Add request body if POST/PUT
        if ("POST".equalsIgnoreCase(endpoint.getMethod()) || "PUT".equalsIgnoreCase(endpoint.getMethod())) {
            Object requestBody = endpoint.getApiLog() != null ? endpoint.getApiLog().getRequestBody() : null;
            String bodyJson = "{}";
            try {
                if (requestBody != null && requestBody instanceof String str && str.trim().startsWith("{")) {
                    bodyJson = objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(objectMapper.readValue(str, Object.class));
                } else if (requestBody != null) {
                    bodyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody);
                }
            } catch (Exception e) {
                bodyJson = "{\"key\": \"value\"}";
            }
            curl.append(" \\\n  -d '").append(bodyJson.replace("\n", "").replace("'", "\\'")).append("'");
        }

        return curl.toString();
    }



    private void loadSamplesFromLogs(Map<String, ApiEndpointInfo> endpoints) {
        try {

            Path logDir = Paths.get(logDirectory);
            if (!Files.exists(logDir)) {
                return;
            }

            endpoints.values().forEach(endpoint -> {
                String fileName = endpoint.getMethod() + "__" +contextPath.replace("/","")+"_"+
                        endpoint.getPath().substring(1).replace("/", "_") + "_latest.json";
                Path logFile = logDir.resolve(fileName);

                if (Files.exists(logFile)) {
                    try {
                        String content = Files.readString(logFile);
                        ApiLog apiEndpointInfo = objectMapper.readValue(content, ApiLog.class);
                        endpoint.setApiLog(apiEndpointInfo);
                    } catch (IOException e) {
                        // Log error if needed
                        log.info("error while saving sample data"+e.toString());
                    }
                }
            });
        } catch (Exception e) {
            // Log error if needed
        }
    }

}