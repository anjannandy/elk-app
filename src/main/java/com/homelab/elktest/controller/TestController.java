package com.homelab.elktest.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api")
public class TestController {

    private static final Logger logger = LoggerFactory.getLogger(TestController.class);
    private static final Random random = new Random();
    private int requestCounter = 0;

    @GetMapping("/hello")
    public Map<String, Object> hello(@RequestParam(defaultValue = "World") String name) {
        requestCounter++;
        logger.info("Received hello request from: {}, request count: {}", name, requestCounter);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Hello, " + name + "!");
        response.put("timestamp", System.currentTimeMillis());
        response.put("requestNumber", requestCounter);

        return response;
    }

    @PostMapping("/process")
    public Map<String, Object> process(@RequestBody Map<String, Object> data) {
        requestCounter++;
        logger.info("Processing data request, request count: {}, data size: {}", requestCounter, data.size());
        logger.debug("Request payload: {}", data);

        // Simulate processing
        int processingTime = random.nextInt(1000) + 100;
        try {
            Thread.sleep(processingTime);
        } catch (InterruptedException e) {
            logger.error("Processing interrupted", e);
            Thread.currentThread().interrupt();
        }

        logger.info("Processing completed in {}ms", processingTime);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "processed");
        response.put("processingTimeMs", processingTime);
        response.put("receivedData", data);
        response.put("requestNumber", requestCounter);

        return response;
    }

    @GetMapping("/simulate-error")
    public Map<String, Object> simulateError(@RequestParam(defaultValue = "false") boolean throwException) {
        requestCounter++;
        logger.warn("Error simulation endpoint called, throwException: {}", throwException);

        if (throwException) {
            logger.error("Simulating application error!");
            throw new RuntimeException("Simulated error for testing ELK stack");
        }

        logger.info("Logged warning without throwing exception");
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Warning logged successfully");
        response.put("requestNumber", requestCounter);

        return response;
    }

    @GetMapping("/generate-logs")
    public Map<String, Object> generateLogs(@RequestParam(defaultValue = "10") int count) {
        logger.info("Starting log generation, count: {}", count);

        for (int i = 0; i < count; i++) {
            int logType = random.nextInt(4);
            switch (logType) {
                case 0:
                    logger.trace("TRACE log entry {} of {}", i + 1, count);
                    break;
                case 1:
                    logger.debug("DEBUG log entry {} of {}: Random value = {}", i + 1, count, random.nextInt(1000));
                    break;
                case 2:
                    logger.info("INFO log entry {} of {}: Operation completed successfully", i + 1, count);
                    break;
                case 3:
                    logger.warn("WARN log entry {} of {}: Potential issue detected", i + 1, count);
                    break;
            }
        }

        logger.info("Log generation completed, generated {} log entries", count);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "completed");
        response.put("logsGenerated", count);
        response.put("requestNumber", ++requestCounter);

        return response;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("totalRequests", requestCounter);
        response.put("timestamp", System.currentTimeMillis());

        return response;
    }
}
