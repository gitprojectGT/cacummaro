package org.cacummaro.service.pdf;

public interface PdfGenerator {

    byte[] generatePdf(String url) throws PdfGenerationException;

    byte[] generatePdf(String url, PdfOptions options) throws PdfGenerationException;

    class PdfOptions {
        private int timeoutSeconds = 30;
        private String format = "A4";
        private boolean fullPage = true;
        private boolean printBackground = true;

        public PdfOptions() {}

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public boolean isFullPage() {
            return fullPage;
        }

        public void setFullPage(boolean fullPage) {
            this.fullPage = fullPage;
        }

        public boolean isPrintBackground() {
            return printBackground;
        }

        public void setPrintBackground(boolean printBackground) {
            this.printBackground = printBackground;
        }
    }
}