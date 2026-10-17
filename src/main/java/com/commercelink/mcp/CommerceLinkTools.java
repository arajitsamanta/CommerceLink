package com.commercelink.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import com.commercelink.domain.Order;
import com.commercelink.domain.OrderLine;
import com.commercelink.domain.OrderService;
import com.commercelink.domain.OrderStatus;
import com.commercelink.domain.Product;
import com.commercelink.domain.ProductService;
import com.commercelink.domain.StockLevel;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CommerceLinkTools {
    private static final McpJsonMapper JSON = McpJsonDefaults.getMapper();
    private final ProductService products;
    private final OrderService orders;

    public CommerceLinkTools(ProductService products, OrderService orders) {
        this.products = products;
        this.orders = orders;
    }

    public List<McpServerFeatures.SyncToolSpecification> specifications() {
        return List.of(
                tool("list_products", "List products, optionally filtered by category. Use this to discover the catalog; prices are in USD and stockQty is on-hand inventory.",
                        schema(Map.of("category", stringSchema("Product category, for example Accessories")), List.of()), this::listProducts),
                tool("check_stock", "Check on-hand, reserved, and available inventory for one product. Use before promising stock; availableQty excludes open orders.",
                        schema(Map.of("productId", stringSchema("Product identifier")), List.of("productId")), this::checkStock),
                tool("create_order", "Create a pending customer order and reserve inventory atomically. Use when a customer wants to buy products; each qty must be a positive integer and total is in USD.",
                        schema(Map.of(
                                "customerId", stringSchema("Customer identifier"),
                                "items", Map.of(
                                        "type", "array",
                                        "minItems", 1,
                                        "items", Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                        "productId", stringSchema("Product identifier"),
                                                        "qty", integerSchema("Positive number of units")),
                                                "required", List.of("productId", "qty"),
                                                "additionalProperties", false))),
                                List.of("customerId", "items")),
                        this::createOrder),
                tool("get_order", "Retrieve one order, including its line items, current status, total in USD, and creation time.",
                        schema(Map.of("orderId", stringSchema("Order identifier")), List.of("orderId")), this::getOrder),
                tool("update_order_status", "Update an order through the legal lifecycle: PENDING to CONFIRMED or CANCELLED; CONFIRMED to SHIPPED or CANCELLED. SHIPPED and CANCELLED are terminal.",
                        schema(Map.of(
                                "orderId", stringSchema("Order identifier"),
                                "status", Map.of("type", "string", "enum", List.of("PENDING", "CONFIRMED", "SHIPPED", "CANCELLED"))),
                                List.of("orderId", "status")),
                        this::updateOrderStatus),
                tool("list_orders", "List recent orders, optionally filtered by status. The limit defaults to 20 and must be between 1 and 100.",
                        schema(Map.of(
                                "status", Map.of("type", "string", "enum", List.of("PENDING", "CONFIRMED", "SHIPPED", "CANCELLED")),
                                "limit", Map.of("type", "integer", "minimum", 1, "maximum", 100)),
                                List.of()),
                        this::listOrders));
    }

    private McpServerFeatures.SyncToolSpecification tool(
            String name,
            String description,
            Map<String, Object> inputSchema,
            ToolHandler handler) {
        McpSchema.Tool spec = McpSchema.Tool.builder(name, inputSchema)
                .description(description)
                .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(spec)
                .callHandler((exchange, request) -> invoke(request, handler))
                .build();
    }

    private CallToolResult invoke(CallToolRequest request, ToolHandler handler) {
        try {
            Object result = handler.call(arguments(request));
            return CallToolResult.builder()
                    .textContent(List.of(JSON.writeValueAsString(result)))
                    .structuredContent(result)
                    .isError(false)
                    .build();
        } catch (IllegalArgumentException exception) {
            return CallToolResult.builder()
                    .content(List.of(TextContent.builder(exception.getMessage()).build()))
                    .isError(true)
                    .build();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not serialize tool response", exception);
        }
    }

    private Object listProducts(Map<String, Object> args) {
        String category = optionalString(args, "category");
        return products.listProducts(category).stream().map(this::productMap).toList();
    }

    private Object checkStock(Map<String, Object> args) {
        return stockMap(products.checkStock(requiredString(args, "productId")));
    }

    private Object createOrder(Map<String, Object> args) {
        String customerId = requiredString(args, "customerId");
        Object rawItems = args.get("items");
        if (!(rawItems instanceof List<?> items) || items.isEmpty()) {
            throw new IllegalArgumentException("items must contain at least one product");
        }
        Map<String, Integer> quantities = new LinkedHashMap<>();
        for (Object rawItem : items) {
            if (!(rawItem instanceof Map<?, ?> item)) {
                throw new IllegalArgumentException("each item must be an object");
            }
            Object productId = item.get("productId");
            Object qty = item.get("qty");
            if (!(productId instanceof String id) || id.isBlank()) {
                throw new IllegalArgumentException("productId is required for every item");
            }
            int quantity = positiveInteger(qty, "qty");
            try {
                quantities.merge(id, quantity, Math::addExact);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("combined qty is too large");
            }
        }
        return orderMap(orders.createOrder(customerId, quantities));
    }

    private Object getOrder(Map<String, Object> args) {
        return orderMap(orders.getOrder(requiredString(args, "orderId")));
    }

    private Object updateOrderStatus(Map<String, Object> args) {
        String rawStatus = requiredString(args, "status");
        OrderStatus status = parseStatus(rawStatus);
        return orderMap(orders.updateOrderStatus(requiredString(args, "orderId"), status));
    }

    private Object listOrders(Map<String, Object> args) {
        OrderStatus status = args.containsKey("status")
                ? parseStatus(requiredString(args, "status"))
                : null;
        int limit = args.containsKey("limit") ? positiveInteger(args.get("limit"), "limit") : 20;
        return orders.listOrders(status, limit).stream().map(this::orderSummaryMap).toList();
    }

    private Map<String, Object> productMap(Product product) {
        return Map.of(
                "id", product.id(),
                "name", product.name(),
                "category", product.category(),
                "price", product.price(),
                "stockQty", product.stockQty());
    }

    private Map<String, Object> stockMap(StockLevel level) {
        return Map.of(
                "productId", level.productId(),
                "stockQty", level.stockQty(),
                "reservedQty", level.reservedQty(),
                "availableQty", level.availableQty());
    }

    private Map<String, Object> orderMap(Order order) {
        List<Map<String, Object>> items = order.items().stream().map(this::lineMap).toList();
        return Map.of(
                "orderId", order.orderId(),
                "customerId", order.customerId(),
                "status", order.status().name(),
                "items", items,
                "total", order.total(),
                "createdAt", order.createdAt().toString());
    }

    private Map<String, Object> orderSummaryMap(Order order) {
        return Map.of(
                "orderId", order.orderId(),
                "customerId", order.customerId(),
                "status", order.status().name(),
                "total", order.total(),
                "createdAt", order.createdAt().toString());
    }

    private Map<String, Object> lineMap(OrderLine line) {
        return Map.of(
                "productId", line.productId(),
                "name", line.name(),
                "qty", line.qty(),
                "unitPrice", line.unitPrice(),
                "lineTotal", line.lineTotal());
    }

    private Map<String, Object> arguments(CallToolRequest request) {
        return request.arguments() == null ? Map.of() : request.arguments();
    }

    private String requiredString(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return string;
    }

    private String optionalString(Map<String, Object> args, String name) {
        if (!args.containsKey(name)) {
            return null;
        }
        return requiredString(args, name);
    }

    private int positiveInteger(Object value, String name) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(name + " must be a positive integer");
        }
        long parsed = number.longValue();
        if (parsed < 1 || parsed > Integer.MAX_VALUE || number.doubleValue() != parsed) {
            throw new IllegalArgumentException(name + " must be a positive integer");
        }
        return (int) parsed;
    }

    private OrderStatus parseStatus(String rawStatus) {
        try {
            return OrderStatus.valueOf(rawStatus);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("status must be one of PENDING, CONFIRMED, SHIPPED, CANCELLED");
        }
    }

    private Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", required,
                "additionalProperties", false);
    }

    private Map<String, Object> stringSchema(String description) {
        return Map.of("type", "string", "description", description);
    }

    private Map<String, Object> integerSchema(String description) {
        return Map.of("type", "integer", "minimum", 1, "description", description);
    }

    @FunctionalInterface
    private interface ToolHandler {
        Object call(Map<String, Object> arguments);
    }
}
