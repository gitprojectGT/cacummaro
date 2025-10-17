package org.cacummaro.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WelcomeController {

    @GetMapping("/")
    public String welcome(Model model) {
        model.addAttribute("appName", "Cacummaro");
        model.addAttribute("version", "1.0-SNAPSHOT");
        model.addAttribute("description", "Web PDF Ingest & Categorization Application");
        return "welcome";
    }

    @GetMapping("/graph")
    public String graph() {
        return "graph";
    }

    @GetMapping("/obsidian")
    public String obsidian() {
        return "obsidian";
    }
}