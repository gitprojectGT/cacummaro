package org.cacummaro.service.obsidian;

public class ObsidianNoteException extends Exception {

    private static final long serialVersionUID = 1L;

    public ObsidianNoteException(String message) {
        super(message);
    }

    public ObsidianNoteException(String message, Throwable cause) {
        super(message, cause);
    }
}