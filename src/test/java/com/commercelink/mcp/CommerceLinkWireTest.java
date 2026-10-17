package com.commercelink.mcp;

import com.commercelink.CommerceLinkApplication;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommerceLinkWireTest {
    private ConfigurableApplicationContext applicationContext;
    private McpSyncClient client;
    private int port;

    @BeforeEach
    void startHttpServer() throws Exception {
        applicationContext = new SpringApplicationBuilder(CommerceLinkApplication.class)
                .web(WebApplicationType.SERVLET)
                .run("--commerce-link.transport=http", "--server.port=0");
        port = ((WebServerApplicationContext) applicationContext).getWebServer().getPort();
        client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                        .endpoint("/mcp")
                        .build())
                .build();
    }

    @AfterEach
    void stopHttpServer() throws Exception {
        if (client != null) {
            client.closeGracefully();
        }
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

    @Test
    void httpClientCanListAndCallTools() {
        var toolSpecs = client.listTools().tools();
        assertEquals(Set.of(
                        "list_products", "check_stock", "create_order",
                        "get_order", "update_order_status", "list_orders"),
                toolSpecs.stream().map(tool -> tool.name()).collect(java.util.stream.Collectors.toSet()));
        var createOrder = toolSpecs.stream().filter(tool -> tool.name().equals("create_order")).findFirst().orElseThrow();
        assertEquals(List.of("customerId", "items"), createOrder.inputSchema().get("required"));

        CallToolResult result = client.callTool(CallToolRequest.builder("check_stock")
                .arguments(Map.of("productId", "p-118"))
                .build());
        assertFalse(Boolean.TRUE.equals(result.isError()));
        assertTrue(result.content().get(0).toString().contains("3"));

        CallToolResult rejectedOrder = client.callTool(CallToolRequest.builder("create_order")
                .arguments(Map.of(
                        "customerId", "c-test",
                        "items", List.of(Map.of("productId", "p-118", "qty", 500))))
                .build());
        assertTrue(Boolean.TRUE.equals(rejectedOrder.isError()));
        assertTrue(rejectedOrder.content().get(0).toString().contains("only 3 units available"));
    }

    @Test
    void servesSwaggerUiAndTheOpenApiSpecLocally() throws Exception {
        HttpClient httpClient = HttpClient.newHttpClient();

        HttpResponse<String> uiResponse = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/swagger-ui/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, uiResponse.statusCode());
        assertTrue(uiResponse.body().contains("SwaggerUIBundle"));
        assertTrue(uiResponse.body().contains("/openapi.yaml"));

        HttpResponse<String> specResponse = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/openapi.yaml")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, specResponse.statusCode());
        assertTrue(specResponse.body().contains("openapi: 3.1.0"));
        assertTrue(specResponse.body().contains("CommerceLink MCP HTTP API"));

        HttpResponse<byte[]> assetResponse = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/swagger-ui/swagger-ui-bundle.js"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, assetResponse.statusCode());
        assertTrue(assetResponse.body().length > 100_000);
    }

    @Test
    void stdioClientCanListAndCallTools() {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");
        StdioClientTransport transport = new StdioClientTransport(
                ServerParameters.builder(java)
                        .args("-cp", classpath, CommerceLinkApplication.class.getName(), "--transport", "stdio")
                        .build(),
                McpJsonDefaults.getMapper());
        try (McpSyncClient stdioClient = McpClient.sync(transport).build()) {
            assertEquals(6, stdioClient.listTools().tools().size());
            CallToolResult result = stdioClient.callTool(CallToolRequest.builder("check_stock")
                    .arguments(Map.of("productId", "p-101"))
                    .build());
            assertFalse(Boolean.TRUE.equals(result.isError()));
            assertTrue(result.content().get(0).toString().contains("40"));
        }
    }
}
