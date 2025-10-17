package org.cacummaro.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class IngestRequest {

    @NotBlank(message = "URL is required")
    private String url;

    @NotNull
    private IngestOptions options = new IngestOptions();

    public IngestRequest() {}

    public IngestRequest(String url, IngestOptions options) {
        this.url = url;
        this.options = options != null ? options : new IngestOptions();
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public IngestOptions getOptions() {
        return options;
    }

    public void setOptions(IngestOptions options) {
        this.options = options;
    }

    public static class IngestOptions {
        private boolean createObsidianNote = true;
        private String noteMetaTag = "data-note";

        public IngestOptions() {}

        public boolean isCreateObsidianNote() {
            return createObsidianNote;
        }

        public void setCreateObsidianNote(boolean createObsidianNote) {
            this.createObsidianNote = createObsidianNote;
        }

        public String getNoteMetaTag() {
            return noteMetaTag;
        }

        public void setNoteMetaTag(String noteMetaTag) {
            this.noteMetaTag = noteMetaTag;
        }
    }
}