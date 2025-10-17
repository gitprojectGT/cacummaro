package org.cacummaro.service;

public class DocumentServiceException extends Exception {

    private static final long serialVersionUID = 1L;

    public DocumentServiceException(String message) {
        super(message);
    }

    public DocumentServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}