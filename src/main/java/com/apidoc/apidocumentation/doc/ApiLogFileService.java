package com.apidoc.apidocumentation.doc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ApiLogFileService {
    
    @Value("${apidoc.log.directory:api-logs}")
    private String logDirectory;
    
    @Value("${apidoc.log.format:json}") // json or txt
    private String logFormat;
    
    @Value("${apidoc.log.replace-latest:true}") // Replace latest log for same endpoint
    private boolean replaceLatest;
    
    private final ObjectMapper objectMapper;
    
    public ApiLogFileService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(logDirectory));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create log directory", e);
        }
    }
    
    public void saveLog(ApiLog log) {
        try {
            String fileName;
            Path filePath;
            
            if (replaceLatest) {
                // Delete old log files for same endpoint if replace mode is enabled
                deleteOldLogsForEndpoint(log);
                fileName = generateLatestFileName(log);
            } else {
                fileName = generateUniqueFileName(log);
            }
            
            filePath = Paths.get(logDirectory, fileName);
            
            if ("json".equalsIgnoreCase(logFormat)) {
                saveAsJson(filePath, log);
            } else {
                saveAsText(filePath, log);
            }
        } catch (IOException e) {
            System.err.println("Failed to save API log: " + e.getMessage());
        }
    }
    
    private void deleteOldLogsForEndpoint(ApiLog log) throws IOException {
        String endpointPattern = sanitizeForFileName(log.getEndpoint());
        String methodPattern = log.getMethod();
        
        Files.list(Paths.get(logDirectory))
            .filter(path -> {
                String fileName = path.getFileName().toString();
                return fileName.contains(methodPattern) && 
                       fileName.contains(endpointPattern) &&
                       fileName.endsWith("." + logFormat);
            })
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    System.err.println("Failed to delete old log: " + path);
                }
            });
    }
    
    private String generateLatestFileName(ApiLog log) {
        String endpoint = sanitizeForFileName(log.getEndpoint());
        return String.format("%s_%s_latest.%s", 
            log.getMethod(), endpoint, logFormat);
    }
    
    private String generateUniqueFileName(ApiLog log) {
        String date = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String timestamp = log.getTimestamp()
            .format(DateTimeFormatter.ofPattern("HHmmss-SSS"));
        String endpoint = sanitizeForFileName(log.getEndpoint());
        
        return String.format("%s_%s_%s_%s.%s", 
            date, log.getMethod(), endpoint, timestamp, logFormat);
    }
    
    private String sanitizeForFileName(String input) {
        return input.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
    
    private void saveAsJson(Path filePath, ApiLog log) throws IOException {
        objectMapper.writeValue(filePath.toFile(), log);
    }
    
    private void saveAsText(Path filePath, ApiLog log) throws IOException {
        StringBuilder content = new StringBuilder();
        content.append("=" .repeat(80)).append("\n");
        content.append("API REQUEST/RESPONSE LOG\n");
        content.append("=" .repeat(80)).append("\n\n");
        
        content.append("Timestamp: ").append(log.getTimestamp()).append("\n");
        content.append("Endpoint: ").append(log.getEndpoint()).append("\n");
        content.append("Method: ").append(log.getMethod()).append("\n");
        content.append("Status Code: ").append(log.getStatusCode()).append("\n");
        content.append("Execution Time: ").append(log.getExecutionTime()).append("ms\n");
        content.append("Client IP: ").append(log.getClientIp()).append("\n\n");
        
        content.append("--- REQUEST HEADERS ---\n");
        if (log.getRequestHeaders() != null) {
            log.getRequestHeaders().forEach((k, v) -> 
                content.append(k).append(": ").append(v).append("\n"));
        }
        
        content.append("\n--- QUERY PARAMETERS ---\n");
        if (log.getQueryParams() != null) {
            log.getQueryParams().forEach((k, v) -> 
                content.append(k).append("=").append(v).append("\n"));
        }
        
        content.append("\n--- PATH VARIABLES ---\n");
        if (log.getPathVariables() != null) {
            log.getPathVariables().forEach((k, v) -> 
                content.append(k).append("=").append(v).append("\n"));
        }
        
        content.append("\n--- REQUEST BODY ---\n");
        content.append(log.getRequestBody() != null ? log.getRequestBody() : "N/A");
        
        content.append("\n\n--- RESPONSE BODY ---\n");
        content.append(log.getResponseBody() != null ? log.getResponseBody() : "N/A");
        
        content.append("\n\n").append("=" .repeat(80)).append("\n");
        
        Files.writeString(filePath, content.toString(), StandardOpenOption.CREATE);
    }
    
    public List<ApiLog> getLogsByEndpoint(String endpoint) {
        try {
            return Files.list(Paths.get(logDirectory))
                .filter(path -> path.toString().contains(sanitizeForFileName(endpoint)))
                .map(this::readLogFromFile)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Failed to read logs: " + e.getMessage());
            return Collections.emptyList();
        }
    }
    
    public List<ApiLog> getLogsByDate(LocalDate date) {
        try {
            String dateStr = date.format(DateTimeFormatter.ISO_DATE);
            return Files.list(Paths.get(logDirectory))
                .filter(path -> path.getFileName().toString().startsWith(dateStr))
                .map(this::readLogFromFile)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Failed to read logs: " + e.getMessage());
            return Collections.emptyList();
        }
    }
    
    public List<ApiLog> getAllLogs() {
        try {
            return Files.list(Paths.get(logDirectory))
                .filter(path -> path.toString().endsWith("." + logFormat))
                .map(this::readLogFromFile)
                .filter(Objects::nonNull)
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Failed to read logs: " + e.getMessage());
            return Collections.emptyList();
        }
    }
    
    private ApiLog readLogFromFile(Path path) {
        try {
            if ("json".equalsIgnoreCase(logFormat)) {
                return objectMapper.readValue(path.toFile(), ApiLog.class);
            }
            // For text format, return basic info
            return null; // Text format reading can be implemented if needed
        } catch (IOException e) {
            System.err.println("Failed to read log file: " + path + " - " + e.getMessage());
            return null;
        }
    }
    
    public void cleanOldLogs(int daysToKeep) {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
            
            Files.list(Paths.get(logDirectory))
                .filter(path -> {
                    try {
                        return Files.getLastModifiedTime(path)
                            .toInstant()
                            .isBefore(cutoffDate.atZone(java.time.ZoneId.systemDefault()).toInstant());
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        System.err.println("Failed to delete old log: " + path);
                    }
                });
        } catch (IOException e) {
            System.err.println("Failed to clean old logs: " + e.getMessage());
        }
    }
}