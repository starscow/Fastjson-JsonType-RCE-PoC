package com.percivalll.fastjson.jsontype;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public class ParseController {
    private final FastjsonHarness harness;
    private final WorkerParser workerParser;

    public ParseController(FastjsonHarness harness, WorkerParser workerParser) {
        this.harness = harness;
        this.workerParser = workerParser;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        return harness.info("request-thread");
    }

    @PostMapping("/parse")
    public Map<String, Object> parse(@RequestBody String payload) {
        return harness.parse("request-thread", payload);
    }

    @PostMapping("/parse-async")
    public CompletableFuture<Map<String, Object>> parseAsync(@RequestBody String payload) {
        return workerParser.parse(payload);
    }
}
