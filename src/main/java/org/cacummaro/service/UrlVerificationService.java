package org.cacummaro.service;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

@Service
public class UrlVerificationService {

    private static final int TIMEOUT_MS = 10000; // 10 seconds

    public UrlVerificationResult verifyUrl(String url) {
        try {
            // Parse and validate URL
            URI uri = new URI(url);
            if (uri.getScheme() == null || (!uri.getScheme().equals("http") && !uri.getScheme().equals("https"))) {
                return new UrlVerificationResult(false, "Invalid URL scheme. Only HTTP and HTTPS are supported.");
            }

            // Check accessibility with HEAD request
            RequestConfig config = RequestConfig.custom()
                    .setSocketTimeout(TIMEOUT_MS)
                    .setConnectTimeout(TIMEOUT_MS)
                    .setConnectionRequestTimeout(TIMEOUT_MS)
                    .build();

            try (CloseableHttpClient httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(config)
                    .build()) {

                HttpHead headRequest = new HttpHead(uri);
                try (CloseableHttpResponse response = httpClient.execute(headRequest)) {
                    int statusCode = response.getStatusLine().getStatusCode();

                    if (statusCode >= 200 && statusCode < 400) {
                        return new UrlVerificationResult(true, "URL is accessible");
                    } else if (statusCode >= 400 && statusCode < 500) {
                        return new UrlVerificationResult(false, "Client error: " + statusCode + " " + response.getStatusLine().getReasonPhrase());
                    } else {
                        return new UrlVerificationResult(false, "Server error: " + statusCode + " " + response.getStatusLine().getReasonPhrase());
                    }
                }
            }

        } catch (URISyntaxException e) {
            return new UrlVerificationResult(false, "Invalid URL format: " + e.getMessage());
        } catch (IOException e) {
            return new UrlVerificationResult(false, "Unable to reach URL: " + e.getMessage());
        } catch (Exception e) {
            return new UrlVerificationResult(false, "Verification failed: " + e.getMessage());
        }
    }

    public static class UrlVerificationResult {
        private final boolean accessible;
        private final String message;

        public UrlVerificationResult(boolean accessible, String message) {
            this.accessible = accessible;
            this.message = message;
        }

        public boolean isAccessible() {
            return accessible;
        }

        public String getMessage() {
            return message;
        }
    }
}