package org.cacummaro.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.ektorp.support.CouchDbDocument;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Document extends CouchDbDocument {

    @JsonProperty("type")
    private String type = "document";

    @JsonProperty("url")
    private String url;

    @JsonProperty("canonicalUrl")
    private String canonicalUrl;

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("metaTags")
    private Map<String, String> metaTags;

    @JsonProperty("fetchedAt")
    private String fetchedAt;

    @JsonProperty("pdfAttachmentName")
    private String pdfAttachmentName;

    @JsonProperty("sizeBytes")
    private Long sizeBytes;

    @JsonProperty("categories")
    private List<CategoryAssignment> categories;

    @JsonProperty("notes")
    private List<String> notes;

    @JsonProperty("status")
    private DocumentStatus status;

    public Document() {}

    public Document(String url) {
        this.url = url;
        this.fetchedAt = Instant.now().toString();
        this.status = DocumentStatus.PROCESSING;
        this.setId("doc|" + java.util.UUID.randomUUID().toString());
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public void setCanonicalUrl(String canonicalUrl) {
        this.canonicalUrl = canonicalUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, String> getMetaTags() {
        return metaTags;
    }

    public void setMetaTags(Map<String, String> metaTags) {
        this.metaTags = metaTags;
    }

    public String getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(String fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt.toString();
    }

    public String getPdfAttachmentName() {
        return pdfAttachmentName;
    }

    public void setPdfAttachmentName(String pdfAttachmentName) {
        this.pdfAttachmentName = pdfAttachmentName;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public List<CategoryAssignment> getCategories() {
        return categories;
    }

    public void setCategories(List<CategoryAssignment> categories) {
        this.categories = categories;
    }

    public List<String> getNotes() {
        return notes;
    }

    public void setNotes(List<String> notes) {
        this.notes = notes;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }
}