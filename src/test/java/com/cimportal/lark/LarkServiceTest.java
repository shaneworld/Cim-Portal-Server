package com.cimportal.lark;

import com.cimportal.auth.CurrentUser;
import com.cimportal.common.error.ApiException;
import com.cimportal.link.Link;
import com.cimportal.setting.SecuritySetting;
import com.cimportal.setting.SecuritySettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LarkServiceTest {

    private SecuritySettingService settings;
    private LarkTokenCache tokenCache;
    private LarkClient client;
    private LarkService service;

    private final CurrentUser user = new CurrentUser("E0026701", "IT", "OPERATOR", false);

    @BeforeEach
    void setup() {
        settings = mock(SecuritySettingService.class);
        tokenCache = mock(LarkTokenCache.class);
        client = mock(LarkClient.class);
        Clock clock = Clock.fixed(Instant.parse("2026-06-14T00:00:00Z"), ZoneOffset.UTC);
        service = new LarkService(settings, tokenCache, client, clock);
    }

    private SecuritySetting newSetting() {
        try {
            var ctor = SecuritySetting.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private SecuritySetting configured() {
        SecuritySetting s = newSetting();
        s.setLarkBaseUrl("https://open.feishu.cn");
        s.setLarkAppId("app");
        s.setLarkAppSecret("secret");
        s.setLarkReceiverId("a@b.com");
        s.setLarkReceiverIdType("email");
        return s;
    }

    private Link link(String nameZh) {
        Link l = new Link(nameZh, "EN", "http://x", "icon", "CAT", "ACTIVE", 1, true);
        return l;
    }

    @Test
    void notConfiguredThrows() {
        SecuritySetting s = configured();
        s.setLarkAppId(null);
        when(settings.get()).thenReturn(s);

        assertThatThrownBy(() -> service.sendAccessRequest(user, link("MES"), "need access"))
            .isInstanceOf(ApiException.class);
        verifyNoInteractions(tokenCache, client);
    }

    @Test
    void accessRequestSendsComposedText() {
        when(settings.get()).thenReturn(configured());
        when(tokenCache.get(any(), any(), any())).thenReturn(Optional.of("t-tok"));
        when(client.sendText(any(), any(), any(), any(), any())).thenReturn(true);

        service.sendAccessRequest(user, link("MES系统"), "需要权限");

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(client).sendText(eq("https://open.feishu.cn"), eq("t-tok"),
            eq("email"), eq("a@b.com"), text.capture());
        assertThat(text.getValue())
            .contains("访问申请")
            .contains("MES系统")
            .contains("E0026701")
            .contains("需要权限");
    }

    @Test
    void accessRequestBlankReasonUsesPlaceholder() {
        when(settings.get()).thenReturn(configured());
        when(tokenCache.get(any(), any(), any())).thenReturn(Optional.of("t-tok"));
        when(client.sendText(any(), any(), any(), any(), any())).thenReturn(true);

        service.sendAccessRequest(user, link("MES"), "  ");

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(client).sendText(any(), any(), any(), any(), text.capture());
        assertThat(text.getValue()).contains("(无)");
    }

    @Test
    void tokenEmptyThrows() {
        when(settings.get()).thenReturn(configured());
        when(tokenCache.get(any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendAccessRequest(user, link("MES"), "x"))
            .isInstanceOf(ApiException.class);
        verifyNoInteractions(client);
    }

    @Test
    void sendFailureThrows() {
        when(settings.get()).thenReturn(configured());
        when(tokenCache.get(any(), any(), any())).thenReturn(Optional.of("t-tok"));
        when(client.sendText(any(), any(), any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.sendFeedback(user, "something broke"))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void feedbackSendsComposedText() {
        when(settings.get()).thenReturn(configured());
        when(tokenCache.get(any(), any(), any())).thenReturn(Optional.of("t-tok"));
        when(client.sendText(any(), any(), any(), any(), any())).thenReturn(true);

        service.sendFeedback(user, "门户很好用");

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(client).sendText(any(), any(), any(), any(), text.capture());
        assertThat(text.getValue())
            .contains("用户反馈")
            .contains("E0026701")
            .contains("门户很好用");
    }

    @Test
    void defaultsBaseUrlAndTypeWhenBlank() {
        SecuritySetting s = configured();
        s.setLarkBaseUrl(null);
        s.setLarkReceiverIdType(null);
        when(settings.get()).thenReturn(s);
        when(tokenCache.get(any(), any(), any())).thenReturn(Optional.of("t-tok"));
        when(client.sendText(any(), any(), any(), any(), any())).thenReturn(true);

        service.sendFeedback(user, "hi");

        verify(tokenCache).get(eq("https://open.feishu.cn"), eq("app"), eq("secret"));
        verify(client).sendText(eq("https://open.feishu.cn"), eq("t-tok"), eq("email"),
            eq("a@b.com"), any());
    }
}
