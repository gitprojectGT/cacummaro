package org.cacummaro.service.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Service
public class PdfTextExtractor {

    private static final Logger logger = LoggerFactory.getLogger(PdfTextExtractor.class);
    private static final int MAX_TEXT_LENGTH = 100000; // Limit to 100k characters

    /**
     * Extract text content from PDF byte array
     *
     * @param pdfData PDF binary data
     * @return Extracted text content
     * @throws IOException if PDF cannot be read
     */
    public String extractText(byte[] pdfData) throws IOException {
        if (pdfData == null || pdfData.length == 0) {
            logger.warn("Cannot extract text from null or empty PDF data");
            return "";
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(pdfData);
             PDDocument document = PDDocument.load(inputStream)) {

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            // Limit text length to prevent memory issues
            if (text.length() > MAX_TEXT_LENGTH) {
                logger.debug("PDF text truncated from {} to {} characters", text.length(), MAX_TEXT_LENGTH);
                text = text.substring(0, MAX_TEXT_LENGTH);
            }

            logger.debug("Extracted {} characters from PDF ({} pages)",
                        text.length(), document.getNumberOfPages());

            return text;

        } catch (IOException e) {
            logger.error("Failed to extract text from PDF: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Extract text from specific page range
     *
     * @param pdfData PDF binary data
     * @param startPage Starting page (1-based)
     * @param endPage Ending page (1-based)
     * @return Extracted text content
     * @throws IOException if PDF cannot be read
     */
    public String extractText(byte[] pdfData, int startPage, int endPage) throws IOException {
        if (pdfData == null || pdfData.length == 0) {
            logger.warn("Cannot extract text from null or empty PDF data");
            return "";
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(pdfData);
             PDDocument document = PDDocument.load(inputStream)) {

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(startPage);
            stripper.setEndPage(Math.min(endPage, document.getNumberOfPages()));

            String text = stripper.getText(document);

            logger.debug("Extracted {} characters from PDF pages {}-{}",
                        text.length(), startPage, endPage);

            return text;

        } catch (IOException e) {
            logger.error("Failed to extract text from PDF pages {}-{}: {}",
                        startPage, endPage, e.getMessage());
            throw e;
        }
    }

    /**
     * Get page count from PDF
     *
     * @param pdfData PDF binary data
     * @return Number of pages
     * @throws IOException if PDF cannot be read
     */
    public int getPageCount(byte[] pdfData) throws IOException {
        if (pdfData == null || pdfData.length == 0) {
            return 0;
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(pdfData);
             PDDocument document = PDDocument.load(inputStream)) {
            return document.getNumberOfPages();
        }
    }
}