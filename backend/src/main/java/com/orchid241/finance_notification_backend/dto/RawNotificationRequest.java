package com.orchid241.finance_notification_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record RawNotificationRequest(
        @NotBlank String packageName,
        String title,
        String text,
        @Positive long postedAt
) {
}