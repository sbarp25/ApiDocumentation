package com.apidoc.apidocumentation.doc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiEndpointInfo {
    private String path;
    private String method;
    private String description;
    private List<String> tags;
    private List<ParamInfo> parameters;
    private List<HeaderInfo> headers;
    private BodyInfo requestBody;
    private BodyInfo responseBody;
    private String className;
    private String methodName;
    private ApiLog apiLog;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ParamInfo {
    private String name;
    private String type;
    private String description;
    private boolean required;
    private String example;
    private String location; // query, path, header
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class HeaderInfo {
    private String name;
    private String description;
    private boolean required;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BodyInfo {
    private String contentType;
    private String schema;
    private String example;
}
