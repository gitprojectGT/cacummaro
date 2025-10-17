package org.cacummaro.service.pdf;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PlaywrightPdfGenerator implements PdfGenerator {

    private static final Logger logger = LoggerFactory.getLogger(PlaywrightPdfGenerator.class);
    private static final int CONTENT_LOAD_WAIT_MS = 2000;

    @Override
    public byte[] generatePdf(String url) throws PdfGenerationException {
        return generatePdf(url, new PdfOptions());
    }

    @Override
    public byte[] generatePdf(String url, PdfOptions options) throws PdfGenerationException {
        logger.info("Generating PDF for URL: {}", url);

        try (Playwright playwright = Playwright.create()) {
            try (Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(java.util.Arrays.asList(
                            "--disable-blink-features=AutomationControlled",
                            "--disable-dev-shm-usage",
                            "--no-sandbox"
                    )))) {

                // Create context with realistic browser settings to avoid bot detection
                try (BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setViewportSize(1920, 1080)
                    .setLocale("en-US")
                    .setTimezoneId("America/New_York")
                        .setExtraHTTPHeaders(java.util.Map.of(
                                "Accept-Language", "en-US,en;q=0.9",
                                "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                                "Sec-Ch-Ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"",
                                "Sec-Ch-Ua-Mobile", "?0",
                                "Sec-Ch-Ua-Platform", "\"Windows\"",
                                "Sec-Fetch-Dest", "document",
                                "Sec-Fetch-Mode", "navigate",
                                "Sec-Fetch-Site", "none",
                                "Sec-Fetch-User", "?1",
                                "Upgrade-Insecure-Requests", "1"
                        )))) {

                    Page page = context.newPage();

                    // Set timeout
                    page.setDefaultTimeout(options.getTimeoutSeconds() * 1000.0);

                    // Navigate to the URL with wait until options
                    page.navigate(url, new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.NETWORKIDLE));

                    // Additional wait to ensure dynamic content loads
                    try {
                        page.waitForTimeout(CONTENT_LOAD_WAIT_MS);
                    } catch (Exception e) {
                        logger.debug("Additional wait interrupted, continuing...");
                    }

                    // Generate PDF
                    Page.PdfOptions pdfOptions = new Page.PdfOptions()
                            .setFormat(options.getFormat())
                            .setPrintBackground(options.isPrintBackground())
                            .setDisplayHeaderFooter(false);

                    byte[] pdfData = page.pdf(pdfOptions);

                    logger.info("Successfully generated PDF for URL: {}, size: {} bytes", url, pdfData.length);
                    return pdfData;
                }
            }
        } catch (PlaywrightException e) {
            logger.error("Failed to generate PDF for URL: {}", url, e);
            throw new PdfGenerationException("Failed to generate PDF: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error generating PDF for URL: {}", url, e);
            throw new PdfGenerationException("Unexpected error: " + e.getMessage(), e);
        }
    }
}