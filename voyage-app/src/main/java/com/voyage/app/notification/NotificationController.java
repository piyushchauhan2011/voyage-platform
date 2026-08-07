package com.voyage.app.notification;

import com.voyage.app.common.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/me")
    public PageResponse<NotificationResponse> getMyNotifications(Authentication authentication,
                                                                 @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return PageResponse.from(notificationService.findForUser(authentication.getName(), pageable));
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationResponse markRead(Authentication authentication, @PathVariable Long notificationId) {
        return notificationService.markRead(authentication.getName(), notificationId);
    }
}