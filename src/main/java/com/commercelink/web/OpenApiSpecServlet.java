package com.commercelink.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;

public final class OpenApiSpecServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/yaml; charset=UTF-8");
        response.setHeader("X-Content-Type-Options", "nosniff");
        try (InputStream spec = getClass().getClassLoader().getResourceAsStream("CommerceLink_Spec.yaml")) {
            if (spec == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            spec.transferTo(response.getOutputStream());
        }
    }
}
