package com.apidoc.apidocumentation.doc;

import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

@Service
public class ApiDocumentationService {
    
    private final ApplicationContext context;
    private ApiDocFileService fileService;
    private final Map<String, ApiEndpointInfo> apiRegistry = new HashMap<>();
    
    public ApiDocumentationService(ApplicationContext context) {
        this.context = context;
    }

    @Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE)
    public void scanAndRegisterApis() {

        Map<String, Object> controllers = context.getBeansWithAnnotation(ApiDocClass.class);
//        Map<String, Object> controllers = context.getBeansWithAnnotation(RestController.class);
//        controllers.putAll(context.getBeansWithAnnotation(Controller.class));

        for (Object controller : controllers.values()) {
            Class<?> clazz = controller.getClass();
            if (clazz.getName().contains("$")) {
                clazz = clazz.getSuperclass();
            }
            String baseUrl = getBaseUrl(clazz);
            
            for (Method method : clazz.getDeclaredMethods()) {
                ApiEndpointInfo info = extractEndpointInfo(method, baseUrl, clazz);
                if (info != null) {
                    String key = info.getMethod() + ":" + info.getPath();
                    apiRegistry.put(key, info);
                }
            }
        }
        
        // Save documentation to file - lazy initialize fileService
        if (fileService == null) {
            fileService = context.getBean(ApiDocFileService.class);
        }
        fileService.saveDocumentation(apiRegistry);
    }
    
    private String getBaseUrl(Class<?> clazz) {
        RequestMapping baseMapping = clazz.getAnnotation(RequestMapping.class);
        return baseMapping != null && baseMapping.value().length > 0 
            ? baseMapping.value()[0] : "";
    }
    
    private ApiEndpointInfo extractEndpointInfo(Method method, String baseUrl, Class<?> clazz) {
        String path = null;
        String httpMethod = null;
        
        if (method.isAnnotationPresent(GetMapping.class)) {
            GetMapping mapping = method.getAnnotation(GetMapping.class);
            path = mapping.value().length > 0 ? mapping.value()[0] : "";
            httpMethod = "GET";
        } else if (method.isAnnotationPresent(PostMapping.class)) {
            PostMapping mapping = method.getAnnotation(PostMapping.class);
            path = mapping.value().length > 0 ? mapping.value()[0] : "";
            httpMethod = "POST";
        } else if (method.isAnnotationPresent(PutMapping.class)) {
            PutMapping mapping = method.getAnnotation(PutMapping.class);
            path = mapping.value().length > 0 ? mapping.value()[0] : "";
            httpMethod = "PUT";
        } else if (method.isAnnotationPresent(DeleteMapping.class)) {
            DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
            path = mapping.value().length > 0 ? mapping.value()[0] : "";
            httpMethod = "DELETE";
        }
        
        if (path == null) return null;
        
        ApiDoc apiDoc = method.getAnnotation(ApiDoc.class);
        String fullPath = baseUrl + path;
        
        return ApiEndpointInfo.builder()
            .path(fullPath)
            .method(httpMethod)
            .description(apiDoc != null ? apiDoc.description() : "")
            .tags(apiDoc != null ? Arrays.asList(apiDoc.tags()) : Collections.emptyList())
            .parameters(extractParameters(method))
            .className(clazz.getName())
            .methodName(method.getName())
            .build();
    }
    
    private List<ParamInfo> extractParameters(Method method) {
        List<ParamInfo> params = new ArrayList<>();
        Parameter[] parameters = method.getParameters();
        
        for (Parameter param : parameters) {
            ApiParam apiParam = param.getAnnotation(ApiParam.class);
            
            if (param.isAnnotationPresent(RequestParam.class)) {
                RequestParam rp = param.getAnnotation(RequestParam.class);
                params.add(ParamInfo.builder()
                    .name(rp.value().isEmpty() ? param.getName() : rp.value())
                    .type(param.getType().getSimpleName())
                    .location("query")
                    .required(rp.required())
                    .description(apiParam != null ? apiParam.description() : "")
                    .build());
            } else if (param.isAnnotationPresent(PathVariable.class)) {
                PathVariable pv = param.getAnnotation(PathVariable.class);
                params.add(ParamInfo.builder()
                    .name(pv.value().isEmpty() ? param.getName() : pv.value())
                    .type(param.getType().getSimpleName())
                    .location("path")
                    .required(true)
                    .description(apiParam != null ? apiParam.description() : "")
                    .build());
            }
        }
        
        return params;
    }
    
    public Map<String, ApiEndpointInfo> getAllEndpoints() {
        return Collections.unmodifiableMap(apiRegistry);
    }
    
    public ApiEndpointInfo getEndpoint(String method, String path) {
        return apiRegistry.get(method + ":" + path);
    }
}