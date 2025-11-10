package com.apidoc.apidocumentation.doc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class ApiLoggingInterceptor implements HandlerInterceptor {

    private final ApiLogFileService logService;
    private static final String START_TIME = "startTime";
    private final ObjectMapper objectMapper;

    public ApiLoggingInterceptor(ApiLogFileService logService, ObjectMapper objectMapper) {
        this.logService = logService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME, System.currentTimeMillis());

        // For GET requests, log parameters (since no body)
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            try {
                String json = objectMapper.writeValueAsString(request.getParameterMap());
                ApiLogContext.setRequestBody(json);
            } catch (Exception ignored) {}
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {

        Long startTime = (Long) request.getAttribute(START_TIME);
        long executionTime = startTime != null ? System.currentTimeMillis() - startTime : 0;

        // Get request body from ThreadLocal
        Object requestBody = ApiLogContext.getRequestBody();

        // Get response body
        Object responseBody = "";
        if (response instanceof ContentCachingResponseWrapper) {
            try {
                String resp;
                resp = new String(((ContentCachingResponseWrapper) response).getContentAsByteArray());
                try {
                    responseBody = objectMapper.readValue(resp, Object.class);
                } catch (JsonProcessingException e) {
                    responseBody = resp;
                }
            } catch (Exception ignored) {}
        }

        ApiLog log = ApiLog.builder()
                .id(UUID.randomUUID().toString())
                .endpoint(request.getRequestURI())
                .method(request.getMethod())
                .requestBody(requestBody)
                .responseBody(responseBody)
                .requestHeaders(extractHeaders(request))
                .queryParams(extractQueryParams(request))
                .pathVariables(extractPathVariables(request))
                .statusCode(response.getStatus())
                .executionTime(executionTime)
                .clientIp(request.getRemoteAddr())
                .timestamp(LocalDateTime.now())
                .build();

        logService.saveLog(log);

        // Clear ThreadLocal
        ApiLogContext.clear();
    }

    private Map<String, String> extractPathVariables(HttpServletRequest request) {
        Map<String, String> pathVars = new HashMap<>();
        Map<String, String> uriTemplateVars = (Map<String, String>)
                request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (uriTemplateVars != null) {
            pathVars.putAll(uriTemplateVars);
        }
        return pathVars;
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }

    private Map<String, String> extractQueryParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) ->
                params.put(key, values.length > 0 ? values[0] : ""));
        return params;
    }
}
