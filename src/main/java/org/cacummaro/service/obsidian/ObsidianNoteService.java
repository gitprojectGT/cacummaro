package org.cacummaro.service.obsidian;

import org.cacummaro.domain.Document;

public interface ObsidianNoteService {

    String createNote(Document document, String noteContent) throws ObsidianNoteException;

    String createNote(Document document, String noteContent, String customMetaTag) throws ObsidianNoteException;

    void deleteNote(String noteFileName) throws ObsidianNoteException;

    boolean noteExists(String noteFileName);
}