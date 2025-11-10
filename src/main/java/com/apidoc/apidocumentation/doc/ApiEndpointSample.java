package com.apidoc.apidocumentation.doc;

import lombok.Data;

@Data
public class ApiEndpointSample{
    // ... existing fields ...
    private Object sampleRequest;
    private Object sampleHeaders;
    private Object sampleResponse;

    // ... add getters and setters for new fields ...
}