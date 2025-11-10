package com.apidoc.apidocumentation.doc;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiLog {
    private String id;
    private String endpoint;
    private String method;
    private Object requestBody;
    private Map<String, String> requestHeaders;
    private Map<String, String> queryParams;
    private Map<String, String> pathVariables;
    private Object responseBody;
    private Integer statusCode;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    private Long executionTime;
    private String clientIp;
}
