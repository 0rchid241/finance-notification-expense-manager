package com.orchid241.finance_notification_backend.controller;

import com.orchid241.finance_notification_backend.dto.RawNotificationRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationController.class);

    @PostMapping("/raw")
    public ResponseEntity<Map<String, String>> receiveRawNotification(
            @Valid @RequestBody RawNotificationRequest request
    ) {
        log.info(
                "알림 수신: packageName={}, title={}, text={}, postedAt={}",
                request.packageName(),
                request.title(),
                request.text(),
                request.postedAt()
        );

        return ResponseEntity
                .accepted()
                .body(Map.of("status", "accepted"));
    }
}