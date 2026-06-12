package com.cimportal.dutyline;

import com.cimportal.dutyline.dto.DutyLineResponse;
import com.cimportal.setting.SecuritySetting;
import com.cimportal.setting.SecuritySettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DutyLineResolveTest {

    private DutyLineRepository repo;
    private OnDutyCache cache;
    private SecuritySettingService settings;
    private DutyLineService service;

    @BeforeEach
    void setup() {
        repo = mock(DutyLineRepository.class);
        cache = mock(OnDutyCache.class);
        settings = mock(SecuritySettingService.class);

        SecuritySetting s = mock(SecuritySetting.class);
        when(s.getDutyApiBaseUrl()).thenReturn("https://duty.example.com");
        when(s.getDutyApiKey()).thenReturn("secret-key");
        when(settings.get()).thenReturn(s);

        service = new DutyLineService(repo, cache, settings);
    }

    private DutyLine line(String phone, String scheduleName) {
        DutyLine dl = new DutyLine("值班", "Duty", phone, 100, true);
        dl.setScheduleName(scheduleName);
        return dl;
    }

    @Test
    void blankScheduleName_usesStaticPhone_noExternalCall() {
        when(repo.findByActiveTrueOrderBySortOrderAsc())
            .thenReturn(List.of(line("12345", null)));

        List<DutyLineResponse> out = service.activeOrdered();

        assertThat(out).hasSize(1);
        assertThat(out.get(0).phone()).isEqualTo("12345");
        assertThat(out.get(0).dutyName()).isNull();
        assertThat(out.get(0).scheduleName()).isNull();
        verifyNoInteractions(cache);
    }

    @Test
    void scheduleNameSet_cacheReturnsPerson_usesExternalNameAndPhone() {
        when(repo.findByActiveTrueOrderBySortOrderAsc())
            .thenReturn(List.of(line("12345", "IT")));
        when(cache.get(any(), any(), eq("IT")))
            .thenReturn(Optional.of(new OnDutyPerson("韩逸水", "16601822375")));

        List<DutyLineResponse> out = service.activeOrdered();

        assertThat(out).hasSize(1);
        assertThat(out.get(0).phone()).isEqualTo("16601822375");
        assertThat(out.get(0).dutyName()).isEqualTo("韩逸水");
        assertThat(out.get(0).scheduleName()).isEqualTo("IT");
    }

    @Test
    void scheduleNameSet_cacheEmpty_fallsBackToStaticPhone() {
        when(repo.findByActiveTrueOrderBySortOrderAsc())
            .thenReturn(List.of(line("12345", "IT")));
        when(cache.get(any(), any(), eq("IT"))).thenReturn(Optional.empty());

        List<DutyLineResponse> out = service.activeOrdered();

        assertThat(out).hasSize(1);
        assertThat(out.get(0).phone()).isEqualTo("12345");
        assertThat(out.get(0).dutyName()).isNull();
        assertThat(out.get(0).scheduleName()).isEqualTo("IT");
    }
}
