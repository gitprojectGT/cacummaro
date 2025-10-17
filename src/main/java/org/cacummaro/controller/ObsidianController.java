package org.cacummaro.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1/obsidian")
@CrossOrigin(origins = "*")
public class ObsidianController {

    @Value("${cacummaro.obsidian.vault-path:./obsidian-vault}")
    private String vaultPath;

    @Value("${cacummaro.obsidian.enabled:true}")
    private boolean enabled;

    @GetMapping("/vault-info")
    public ResponseEntity<?> getVaultInfo() {
        Map<String, Object> info = new HashMap<>();

        try {
            Path vault = Paths.get(vaultPath).toAbsolutePath();
            info.put("vaultPath", vaultPath);  // Use relative path from config
            info.put("enabled", enabled);
            info.put("exists", Files.exists(vault));

            if (Files.exists(vault)) {
                long noteCount = Files.walk(vault)
                    .filter(p -> p.toString().endsWith(".md"))
                    .count();
                info.put("noteCount", noteCount);

                // Get last modified time
                Optional<Path> lastModified = Files.walk(vault)
                    .filter(p -> p.toString().endsWith(".md"))
                    .max(Comparator.comparingLong(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            return 0;
                        }
                    }));

                if (lastModified.isPresent()) {
                    Instant lastModifiedTime = Files.getLastModifiedTime(lastModified.get()).toInstant();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
                        .withZone(ZoneId.systemDefault());
                    info.put("lastUpdated", formatter.format(lastModifiedTime));
                }
            }

            return ResponseEntity.ok(info);
        } catch (Exception e) {
            info.put("error", "Failed to read vault info: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(info);
        }
    }

    @GetMapping("/notes")
    public ResponseEntity<?> listNotes() {
        if (!enabled) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        try {
            Path vault = Paths.get(vaultPath).toAbsolutePath();

            if (!Files.exists(vault)) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            try (Stream<Path> paths = Files.walk(vault)) {
                List<Map<String, Object>> notes = paths
                    .filter(p -> p.toString().endsWith(".md"))
                    .map(p -> {
                        try {
                            Map<String, Object> noteInfo = new HashMap<>();
                            noteInfo.put("fileName", p.getFileName().toString());
                            noteInfo.put("relativePath", vault.relativize(p).toString());
                            noteInfo.put("absolutePath", p.toAbsolutePath().toString());
                            noteInfo.put("size", Files.size(p));
                            noteInfo.put("modified", Files.getLastModifiedTime(p).toInstant().toString());

                            // Try to extract title from first line
                            try {
                                String firstLine = Files.lines(p, java.nio.charset.StandardCharsets.UTF_8).findFirst().orElse("");
                                if (firstLine.startsWith("# ")) {
                                    noteInfo.put("title", firstLine.substring(2).trim());
                                } else {
                                    noteInfo.put("title", p.getFileName().toString().replace(".md", ""));
                                }
                            } catch (Exception e) {
                                // Fallback to filename if can't read content
                                noteInfo.put("title", p.getFileName().toString().replace(".md", ""));
                            }

                            return noteInfo;
                        } catch (IOException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .sorted((a, b) -> {
                        // Sort by modified time, newest first
                        String timeA = (String) a.get("modified");
                        String timeB = (String) b.get("modified");
                        return timeB.compareTo(timeA);
                    })
                    .collect(Collectors.toList());

                return ResponseEntity.ok(notes);
            }
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to list notes: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/notes/{fileName}/content")
    public ResponseEntity<?> getNoteContent(@PathVariable String fileName) {
        if (!enabled) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Obsidian integration is disabled");
        }

        try {
            // Sanitize fileName to prevent path traversal
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                return ResponseEntity.badRequest().body("Invalid file name");
            }

            Path vault = Paths.get(vaultPath).toAbsolutePath();
            Path notePath = vault.resolve(fileName);

            // Ensure the resolved path is still within the vault
            if (!notePath.toAbsolutePath().startsWith(vault)) {
                return ResponseEntity.badRequest().body("Invalid file path");
            }

            if (!Files.exists(notePath)) {
                return ResponseEntity.notFound().build();
            }

            String content = Files.readString(notePath, java.nio.charset.StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(content);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to read note: " + e.getMessage());
        }
    }

    @GetMapping("/notes/{fileName}/download")
    public ResponseEntity<Resource> downloadNote(@PathVariable String fileName) {
        if (!enabled) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            // Sanitize fileName to prevent path traversal
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                return ResponseEntity.badRequest().build();
            }

            Path vault = Paths.get(vaultPath).toAbsolutePath();
            Path notePath = vault.resolve(fileName);

            // Ensure the resolved path is still within the vault
            if (!notePath.toAbsolutePath().startsWith(vault)) {
                return ResponseEntity.badRequest().build();
            }

            if (!Files.exists(notePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(notePath);

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
