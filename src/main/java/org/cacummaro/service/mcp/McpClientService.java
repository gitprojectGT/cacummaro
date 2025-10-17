package org.cacummaro.service.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MCP (Model Context Protocol) Client Service
 *
 * Communicates with an MCP server using JSON-RPC 2.0 protocol over HTTP
 * for enhanced document classification using AI models.
 */
@Service
public class McpClientService {

    private static final Logger logger = LoggerFactory.getLogger(McpClientService.class);

    @Value("${cacummaro.mcp.enabled:false}")
    private boolean enabled;

    @Value("${cacummaro.mcp.server-url:http://localhost:3000}")
    private String serverUrl;

    @Value("${cacummaro.mcp.timeout-ms:30000}")
    private int timeoutMs;

    @Value("${cacummaro.mcp.tool-name:classify_document}")
    private String classifyToolName;

    private final ObjectMapper objectMapper;
    private CloseableHttpClient httpClient;

    @Autowired
    public McpClientService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initialize() {
        if (!enabled) {
            logger.info("MCP Client is disabled. Set cacummaro.mcp.enabled=true to enable");
            return;
        }

        // Initialize HTTP client
        httpClient = HttpClients.createDefault();
        logger.info("MCP Client initialized. Server URL: {}", serverUrl);

        // Test connection
        try {
            testConnection();
            logger.info("MCP Server connection test successful");
        } catch (Exception e) {
            logger.warn("MCP Server connection test failed: {}. Classification will continue without MCP.",
                       e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        if (httpClient != null) {
            try {
                httpClient.close();
                logger.info("MCP Client closed");
            } catch (IOException e) {
                logger.error("Error closing MCP HTTP client: {}", e.getMessage());
            }
        }
    }

    /**
     * Classify document using MCP server
     *
     * @param documentId Document ID
     * @param title Document title
     * @param description Document description
     * @param content Full document content (metadata + PDF text)
     * @return Classification result from MCP server
     * @throws IOException if communication with MCP server fails
     */
    public McpClassificationResult classifyDocument(
            String documentId,
            String title,
            String description,
            String content) throws IOException {

        if (!enabled) {
            throw new IOException("MCP Client is disabled");
        }

        logger.debug("Calling MCP server to classify document: {}", documentId);

        // Prepare request parameters
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("documentId", documentId);
        arguments.put("title", title != null ? title : "");
        arguments.put("description", description != null ? description : "");
        arguments.put("content", content != null ? content : "");

        Map<String, Object> params = new HashMap<>();
        params.put("name", classifyToolName);
        params.put("arguments", arguments);

        // Create MCP request
        McpRequest request = new McpRequest(
            UUID.randomUUID().toString(),
            "tools/call",
            params
        );

        // Send request and get response
        McpResponse response = sendRequest(request);

        if (response.hasError()) {
            throw new IOException("MCP Server error: " + response.getError().getMessage());
        }

        // Parse result
        McpClassificationResult result = objectMapper.convertValue(
            response.getResult(),
            McpClassificationResult.class
        );

        logger.debug("MCP classification completed for document {}: {} categories",
                    documentId,
                    result.getCategories() != null ? result.getCategories().size() : 0);

        return result;
    }

    /**
     * Send JSON-RPC request to MCP server
     *
     * @param request MCP request
     * @return MCP response
     * @throws IOException if communication fails
     */
    private McpResponse sendRequest(McpRequest request) throws IOException {
        HttpPost httpPost = new HttpPost(serverUrl);

        // Serialize request to JSON
        String requestJson = objectMapper.writeValueAsString(request);
        logger.debug("MCP Request: {}", requestJson);

        // Set request entity
        StringEntity entity = new StringEntity(requestJson, ContentType.APPLICATION_JSON);
        httpPost.setEntity(entity);

        // Set headers
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setHeader("Accept", "application/json");

        // Execute request
        try (CloseableHttpResponse httpResponse = httpClient.execute(httpPost)) {
            int statusCode = httpResponse.getCode();
            String responseBody = EntityUtils.toString(httpResponse.getEntity());

            logger.debug("MCP Response (status {}): {}", statusCode, responseBody);

            if (statusCode != 200) {
                throw new IOException("MCP Server returned status " + statusCode + ": " + responseBody);
            }

            // Parse response
            McpResponse response = objectMapper.readValue(responseBody, McpResponse.class);
            return response;

        } catch (ParseException e) {
            logger.error("Failed to parse MCP server response: {}", e.getMessage());
            throw new IOException("Failed to parse MCP response", e);
        } catch (IOException e) {
            logger.error("Failed to communicate with MCP server at {}: {}", serverUrl, e.getMessage());
            throw e;
        }
    }

    /**
     * Test connection to MCP server
     *
     * @throws IOException if connection test fails
     */
    private void testConnection() throws IOException {
        logger.debug("Testing MCP server connection...");

        Map<String, Object> params = new HashMap<>();
        McpRequest request = new McpRequest(
            UUID.randomUUID().toString(),
            "ping",
            params
        );

        try {
            sendRequest(request);
        } catch (Exception e) {
            // Ping might not be supported, try listing tools instead
            params.clear();
            request.setMethod("tools/list");
            sendRequest(request);
        }
    }

    /**
     * List available tools on MCP server
     *
     * @return List of available tools
     * @throws IOException if communication fails
     */
    public McpResponse listTools() throws IOException {
        if (!enabled) {
            throw new IOException("MCP Client is disabled");
        }

        Map<String, Object> params = new HashMap<>();
        McpRequest request = new McpRequest(
            UUID.randomUUID().toString(),
            "tools/list",
            params
        );

        return sendRequest(request);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getServerUrl() {
        return serverUrl;
    }
}