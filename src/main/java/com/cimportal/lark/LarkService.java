package com.cimportal.lark;

import com.cimportal.auth.CurrentUser;
import com.cimportal.common.error.ApiException;
import com.cimportal.link.Link;
import com.cimportal.setting.SecuritySetting;
import com.cimportal.setting.SecuritySettingService;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Composes and sends portal notifications to Lark (Feishu). Reads the Lark config
 * from the security_setting singleton. Fire-and-forget (no DB), but surfaces a
 * non-2xx error to the caller when not configured / auth fails / send fails so the
 * frontend can show a failure.
 */
@Component
public class LarkService {

    private static final String DEFAULT_BASE_URL = "https://open.feishu.cn";
    private static final String DEFAULT_RECEIVER_ID_TYPE = "email";

    private final SecuritySettingService settings;
    private final LarkTokenCache tokenCache;
    private final LarkClient client;
    private final Clock clock;

    public LarkService(SecuritySettingService settings, LarkTokenCache tokenCache,
                       LarkClient client, Clock clock) {
        this.settings = settings;
        this.tokenCache = tokenCache;
        this.client = client;
        this.clock = clock;
    }

    public void sendAccessRequest(CurrentUser user, Link link, String reason) {
        String text = "【访问申请】\n申请人:" + user.employeeId() + " · 部门 " + user.departmentCode()
            + "\n系统:" + link.getNameZh()
            + "\n说明:" + (reason == null || reason.isBlank() ? "(无)" : reason)
            + "\n时间:" + clock.instant();
        send(cfg(), text);
    }

    public void sendFeedback(CurrentUser user, String message) {
        String text = "【用户反馈】\n来自:" + user.employeeId() + " · 部门 " + user.departmentCode()
            + "\n内容:" + message
            + "\n时间:" + clock.instant();
        send(cfg(), text);
    }

    private void send(Cfg c, String text) {
        String token = tokenCache.get(c.baseUrl(), c.appId(), c.appSecret())
            .orElseThrow(() -> ApiException.badRequest("Lark 鉴权失败"));
        if (!client.sendText(c.baseUrl(), token, c.receiverIdType(), c.receiverId(), text)) {
            throw ApiException.badRequest("Lark 发送失败");
        }
    }

    private Cfg cfg() {
        SecuritySetting s = settings.get();
        if (isBlank(s.getLarkAppId()) || isBlank(s.getLarkAppSecret()) || isBlank(s.getLarkReceiverId())) {
            throw ApiException.badRequest("Lark 未配置");
        }
        String baseUrl = isBlank(s.getLarkBaseUrl()) ? DEFAULT_BASE_URL : s.getLarkBaseUrl();
        String type = isBlank(s.getLarkReceiverIdType()) ? DEFAULT_RECEIVER_ID_TYPE : s.getLarkReceiverIdType();
        return new Cfg(baseUrl, s.getLarkAppId(), s.getLarkAppSecret(), s.getLarkReceiverId(), type);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private record Cfg(String baseUrl, String appId, String appSecret, String receiverId, String receiverIdType) { }
}
