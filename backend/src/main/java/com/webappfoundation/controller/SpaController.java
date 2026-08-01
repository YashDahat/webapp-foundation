package com.webappfoundation.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    // Match the SPA fallback at ANY depth: "/{path}" alone only matches a single
    // segment, so deep links like /admin/trainers or /trainers/:id 404 on direct
    // load/refresh. The "/**/{path}" variant covers nested routes. The [^\.] guard
    // keeps real static assets (*.js, *.css, *.webp) served by the resource handler,
    // and explicit @*Mapping controllers still win over this wildcard (so /api/** is unaffected).
    @GetMapping(value = {"/", "/{path:[^\.]*}", "/**/{path:[^\.]*}"})
    public String forward() {
        return "forward:/index.html";
    }
}
