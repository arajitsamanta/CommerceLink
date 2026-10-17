
package com.commercelink;

import com.commercelink.domain.OrderService;
import com.commercelink.domain.ProductService;
import com.commercelink.mcp.CommerceLinkTools;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.context.WebServerApplicationContext;

import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
public class CommerceLinkApplication {
    public static void main(String[] args) {
        String[] springArgs = normalizeArgs(args);
        String transport = transportFrom(springArgs);

        SpringApplication application = new SpringApplication(CommerceLinkApplication.class);
        application.setBannerMode(Banner.Mode.OFF);
        application.setWebApplicationType("http".equals(transport)
                ? org.springframework.boot.WebApplicationType.SERVLET
                : org.springframework.boot.WebApplicationType.NONE);
        application.run(springArgs);
    }

    @Bean
    ProductService productService() {
        return new ProductService();
    }

    @Bean
    OrderService orderService(ProductService products) {
        return new OrderService(products);
    }

    @Bean
    CommerceLinkTools commerceLinkTools(ProductService products, OrderService orders) {
        return new CommerceLinkTools(products, orders);
    }

    @Bean(destroyMethod = "closeGracefully")
    @ConditionalOnProperty(name = "commerce-link.transport", havingValue = "stdio", matchIfMissing = true)
    McpSyncServer stdioMcpServer(CommerceLinkTools tools) {
        return McpServer.sync(new StdioServerTransportProvider(McpJsonDefaults.getMapper()))
                .serverInfo("commercelink", "1.0.0")
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(tools.specifications())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "commerce-link.transport", havingValue = "http")
    HttpServletStreamableServerTransportProvider httpTransportProvider() {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(McpJsonDefaults.getMapper())
                .mcpEndpoint("/mcp")
                .build();
    }

    @Bean(destroyMethod = "closeGracefully")
    @ConditionalOnProperty(name = "commerce-link.transport", havingValue = "http")
    McpSyncServer httpMcpServer(
            HttpServletStreamableServerTransportProvider provider, CommerceLinkTools tools) {
        return McpServer.sync(provider)
                .serverInfo("commercelink", "1.0.0")
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(tools.specifications())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "commerce-link.transport", havingValue = "http")
    ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            HttpServletStreamableServerTransportProvider provider) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
                new ServletRegistrationBean<>(provider, "/*");
        registration.setName("mcpServlet");
        registration.setLoadOnStartup(1);
        registration.setAsyncSupported(true);
        return registration;
    }

    @Bean
    @ConditionalOnProperty(name = "commerce-link.transport", havingValue = "http")
    ServletRegistrationBean<com.commercelink.web.SwaggerUiServlet> swaggerUiServlet() {
        return new ServletRegistrationBean<>(
                new com.commercelink.web.SwaggerUiServlet(),
                "/swagger-ui", "/swagger-ui/", "/swagger-ui/*");
    }

    @Bean
    @ConditionalOnProperty(name = "commerce-link.transport", havingValue = "http")
    ServletRegistrationBean<com.commercelink.web.OpenApiSpecServlet> openApiSpecServlet() {
        return new ServletRegistrationBean<>(
                new com.commercelink.web.OpenApiSpecServlet(), "/openapi.yaml");
    }

    @Bean
    @ConditionalOnProperty(name = "commerce-link.transport", havingValue = "http")
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatAsyncTimeout() {
        return factory -> factory.addConnectorCustomizers(connector -> connector.setAsyncTimeout(30_000));
    }

    @Bean
    org.springframework.boot.ApplicationRunner validateTransport(
            Environment environment, ObjectProvider<WebServerApplicationContext> webContextProvider) {
        return args -> {
            String transport = environment.getProperty("commerce-link.transport", "stdio");
            if (!List.of("stdio", "http").contains(transport)) {
                throw new IllegalArgumentException("transport must be stdio or http");
            }
            if ("http".equals(transport)) {
                int port = webContextProvider.getObject().getWebServer().getPort();
                System.err.printf("CommerceLink MCP server listening at http://localhost:%d/mcp%n", port);
            }
        };
    }

    private static String[] normalizeArgs(String[] args) {
        List<String> normalized = new ArrayList<>();
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if ("--transport".equals(argument) || "--port".equals(argument)) {
                if (index + 1 >= args.length) {
                    throw new IllegalArgumentException(argument + " requires a value");
                }
                String property = "--transport".equals(argument)
                        ? "--commerce-link.transport="
                        : "--server.port=";
                normalized.add(property + args[++index]);
            } else {
                normalized.add(argument);
            }
        }
        return normalized.toArray(String[]::new);
    }

    private static String transportFrom(String[] args) {
        for (String argument : args) {
            if (argument.startsWith("--commerce-link.transport=")) {
                return argument.substring("--commerce-link.transport=".length());
            }
        }
        return System.getProperty("commerce-link.transport",
                System.getenv().getOrDefault("COMMERCE_LINK_TRANSPORT", "stdio"));
    }
}
