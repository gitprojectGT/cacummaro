package org.cacummaro.dto;

import org.cacummaro.domain.DocumentStatus;

public class IngestResponse {

    private String id;
    private DocumentStatus status;
    private String pdfUrl;

    public IngestResponse() {}

    public IngestResponse(String id, DocumentStatus status, String pdfUrl) {
        this.id = id;
        this.status = status;
        this.pdfUrl = pdfUrl;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public String getPdfUrl() {
        return pdfUrl;
    }

    public void setPdfUrl(String pdfUrl) {
        this.pdfUrl = pdfUrl;
    }
}