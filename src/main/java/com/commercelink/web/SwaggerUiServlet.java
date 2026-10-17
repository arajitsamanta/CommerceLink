package com.commercelink.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public final class SwaggerUiServlet extends HttpServlet {
    private static final String ASSET_ROOT = "META-INF/resources/webjars/swagger-ui/5.33.2/";
    private static final Map<String, String> ASSETS = Map.of(
            "swagger-ui.css", "text/css; charset=UTF-8",
            "swagger-ui-bundle.js", "application/javascript; charset=UTF-8",
            "swagger-ui-standalone-preset.js", "application/javascript; charset=UTF-8",
            "favicon-32x32.png", "image/png");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getPathInfo();
        if (path == null || path.isEmpty() || "/".equals(path) || "/index.html".equals(path)) {
            serveUi(response);
            return;
        }

        String assetName = path.substring(1);
        String contentType = ASSETS.get(assetName);
        if (contentType == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try (InputStream asset = getClass().getClassLoader().getResourceAsStream(ASSET_ROOT + assetName)) {
            if (asset == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            response.setContentType(contentType);
            response.setHeader("X-Content-Type-Options", "nosniff");
            asset.transferTo(response.getOutputStream());
        }
    }

    private void serveUi(HttpServletResponse response) throws IOException {
        response.setContentType("text/html; charset=UTF-8");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getWriter().write("""
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>CommerceLink API</title>
                  <link rel="icon" type="image/png" href="/swagger-ui/favicon-32x32.png">
                  <link rel="stylesheet" href="/swagger-ui/swagger-ui.css">
                  <style>html { box-sizing: border-box; overflow-y: scroll; } *, *:before, *:after { box-sizing: inherit; } body { margin: 0; background: #fafafa; }</style>
                </head>
                <body>
                  <div id="swagger-ui"></div>
                  <script src="/swagger-ui/swagger-ui-bundle.js"></script>
                  <script src="/swagger-ui/swagger-ui-standalone-preset.js"></script>
                  <script>
                    window.onload = () => {
                      window.ui = SwaggerUIBundle({
                        url: "/openapi.yaml",
                        dom_id: "#swagger-ui",
                        deepLinking: true,
                        presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
                        layout: "StandaloneLayout"
                      });
                    };
                  </script>
                </body>
                </html>
                """);
    }
}
