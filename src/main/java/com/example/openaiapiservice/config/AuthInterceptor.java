package com.example.openaiapiservice.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component public class AuthInterceptor implements HandlerInterceptor {
    private void writeCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isEmpty()) {
            response.setHeader("Access-Control-Allow-Origin", origin);
        }
        else {
            response.setHeader("Access-Control-Allow-Origin", "*");
        }
        response.setHeader("Vary", "Origin");
        response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Credentials", "true");
    }
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException { // 放行 CORS 预检请求（不继续进入后续链路，直接返回 200） if ("OPTIONS".equalsIgnoreCase(request.getMethod())) { writeCorsHeaders(request, response); response.setStatus(HttpStatus.OK.value()); response.flushBuffer(); return false; }
        String auth = request.getHeader("Authorization");
        boolean invalid = true;
        if (auth != null) {
            String trimmed = auth.trim();
            // Bearer 前缀大小写不敏感
            if (trimmed.toLowerCase().startsWith("bearer ")) {
                String token = trimmed.substring(7).trim();
                // MVP：token 非空即视为有效
                if (!token.isEmpty()) {
                    invalid = false;
                }
            }
        }

        if (invalid) {
            writeCorsHeaders(request, response);
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setHeader("WWW-Authenticate", "Bearer");
            response.setContentType("application/json;charset=UTF-8");
            String body = "{\"error\":{\"message\":\"Invalid API key\",\"type\":\"authentication_error\"}}";
            response.getWriter().write(body);
            response.flushBuffer();
            return false;
        }

        return true;
    }
}