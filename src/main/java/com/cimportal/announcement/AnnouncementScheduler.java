package com.cimportal.announcement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class AnnouncementScheduler {
    private static final Logger log = LoggerFactory.getLogger(AnnouncementScheduler.class);
    private final AnnouncementService service;
    public AnnouncementScheduler(AnnouncementService service) { this.service = service; }

    @Scheduled(fixedDelayString = "300000", initialDelayString = "30000")
    public void closeExpired() {
        int n = service.closeExpired();
        if (n > 0) log.info("Auto-closed {} expired announcement(s)", n);
        int upcoming = service.publishCheck();
        if (upcoming > 0) log.info("{} scheduled announcement(s) upcoming", upcoming);
    }
}
