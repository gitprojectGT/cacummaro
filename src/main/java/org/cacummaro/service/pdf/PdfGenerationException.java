package org.cacummaro.service.pdf;

public class PdfGenerationException extends Exception {

    private static final long serialVersionUID = 1L;

    public PdfGenerationException(String message) {
        super(message);
    }

    public PdfGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}