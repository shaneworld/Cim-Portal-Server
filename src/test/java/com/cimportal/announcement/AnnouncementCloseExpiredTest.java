package com.cimportal.announcement;

import com.cimportal.announcement.dto.AnnouncementRequest;
import com.cimportal.enumvalue.EnumCategory;
import com.cimportal.enumvalue.EnumValue;
import com.cimportal.enumvalue.EnumValueRepository;
import com.cimportal.support.OracleIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AnnouncementCloseExpiredTest extends OracleIntegrationTest {

    @Autowired AnnouncementService service;
    @Autowired AnnouncementRepository repo;
    @Autowired EnumValueRepository enumRepo;

    @BeforeEach
    void seed() {
        repo.deleteAll();
        if (enumRepo.findByCategoryAndCode(EnumCategory.ANNOUNCEMENT_TYPE, "INFO").isEmpty()) {
            EnumValue ev = new EnumValue(EnumCategory.ANNOUNCEMENT_TYPE, "INFO", "通知", "Info", 10, true);
            ev.setColor("blue");
            ev.setIcon("info");
            enumRepo.save(ev);
        }
    }

    private Announcement save(boolean active, Instant endsAt) {
        return repo.save(new Announcement(
            "标题", "Title", "正文", "Body", "INFO", false, null, endsAt, active));
    }

    @Test
    void closeExpired_onlyClosesActiveWithPastEnd() {
        Instant past   = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);

        Announcement a1 = save(true, past);    // should be closed
        Announcement a2 = save(true, null);    // unchanged (no end time)
        Announcement a3 = save(true, future);  // unchanged (future)
        Announcement a4 = save(false, past);   // unchanged (already inactive)

        int closed = service.closeExpired();
        assertThat(closed).isEqualTo(1);

        Announcement r1 = repo.findById(a1.getId()).orElseThrow();
        assertThat(r1.isActive()).isFalse();
        assertThat(r1.getClosedAt()).isNotNull();

        Announcement r2 = repo.findById(a2.getId()).orElseThrow();
        assertThat(r2.isActive()).isTrue();
        assertThat(r2.getClosedAt()).isNull();

        Announcement r3 = repo.findById(a3.getId()).orElseThrow();
        assertThat(r3.isActive()).isTrue();
        assertThat(r3.getClosedAt()).isNull();

        Announcement r4 = repo.findById(a4.getId()).orElseThrow();
        assertThat(r4.isActive()).isFalse();
        assertThat(r4.getClosedAt()).isNull();
    }

    @Test
    void publishCheck_countsActiveFutureStart() {
        Instant now = Instant.now();
        // active=true, startsAt future, endsAt null  → counts
        repo.save(new Announcement(
            "标题", "Title", "正文", "Body", "INFO", false, now.plusSeconds(3600), null, true));
        // active=true, startsAt past, endsAt null     → not counted (already started)
        repo.save(new Announcement(
            "标题", "Title", "正文", "Body", "INFO", false, now.minusSeconds(3600), null, true));
        // active=true, startsAt null, endsAt null      → not counted (no start)
        repo.save(new Announcement(
            "标题", "Title", "正文", "Body", "INFO", false, null, null, true));
        // active=false, startsAt future, endsAt null   → not counted (inactive)
        repo.save(new Announcement(
            "标题", "Title", "正文", "Body", "INFO", false, now.plusSeconds(3600), null, false));

        assertThat(service.publishCheck()).isEqualTo(1);
    }

    @Test
    void update_reEnable_clearsClosedAt() {
        Announcement a = new Announcement(
            "标题", "Title", "正文", "Body", "INFO", false, null,
            Instant.now().minus(1, ChronoUnit.HOURS), false);
        a.setClosedAt(Instant.now().minus(30, ChronoUnit.MINUTES));
        a = repo.save(a);

        AnnouncementRequest req = new AnnouncementRequest(
            "标题", "Title", "正文", "Body", "INFO", false, null, null, true);
        service.update(a.getId(), req);

        Announcement reloaded = repo.findById(a.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isTrue();
        assertThat(reloaded.getClosedAt()).isNull();
    }
}
