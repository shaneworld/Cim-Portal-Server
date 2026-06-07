package com.cimportal.seed;

import com.cimportal.common.AppConstants;
import com.cimportal.enumvalue.EnumCategory;
import com.cimportal.enumvalue.EnumValue;
import com.cimportal.enumvalue.EnumValueRepository;
import com.cimportal.link.*;
import com.cimportal.user.UserInfo;
import com.cimportal.user.UserInfoRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@Profile({AppConstants.Profiles.DEV, AppConstants.Profiles.UAT})
public class DevDataSeeder implements ApplicationRunner {

    private final EnumValueRepository enums;
    private final LinkRepository links;
    private final LinkAccessGrantRepository grants;
    private final UserInfoRepository users;

    public DevDataSeeder(EnumValueRepository enums, LinkRepository links,
                         LinkAccessGrantRepository grants,
                         UserInfoRepository users) {
        this.enums = enums; this.links = links; this.grants = grants;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (enums.count() == 0) seedEnums();
        if (users.count() == 0) seedUsers();
        if (links.count() == 0) seedLinks();
    }

    private void seedEnums() {
        enums.save(new EnumValue(EnumCategory.DEPARTMENT, "FAB1-PROD", "一厂生产", "FAB1 Production", 10, true));
        enums.save(new EnumValue(EnumCategory.DEPARTMENT, "QA", "质量", "Quality", 20, true));
        enums.save(new EnumValue(EnumCategory.DEPARTMENT, "IT", "信息技术", "IT", 30, true));
        enums.save(new EnumValue(EnumCategory.ROLE, "OPERATOR", "操作员", "Operator", 10, true));
        enums.save(new EnumValue(EnumCategory.ROLE, "PROCESS_ENGINEER", "工艺工程师", "Process Engineer", 20, true));
        enums.save(new EnumValue(EnumCategory.ROLE, "QA_ENGINEER", "质量工程师", "QA Engineer", 30, true));
        enums.save(new EnumValue(EnumCategory.ROLE, "PORTAL_ADMIN", "门户管理员", "Portal Admin", 40, true));
        enums.save(new EnumValue(EnumCategory.LINK_CATEGORY, "MES", "制造执行", "MES", 10, true));
        enums.save(new EnumValue(EnumCategory.LINK_CATEGORY, "QUALITY", "质量", "Quality", 20, true));
        enums.save(new EnumValue(EnumCategory.LINK_CATEGORY, "MAINTENANCE", "设备维护", "Maintenance", 30, true));
        enums.save(new EnumValue(EnumCategory.LINK_STATUS, "ACTIVE", "启用", "Active", 10, true));
        enums.save(new EnumValue(EnumCategory.LINK_STATUS, "MAINTENANCE", "维护中", "Maintenance", 20, true));
    }

    private void seedUsers() {
        Instant now = Instant.now();
        users.save(new UserInfo("OP1", "欧阳操作", "Olivia Operator", "FAB1-PROD", "OPERATOR", "op1@example.com", true, now));
        users.save(new UserInfo("ENG1", "伊森工程", "Ethan Engineer", "FAB1-PROD", "PROCESS_ENGINEER", "eng1@example.com", true, now));
        users.save(new UserInfo("QA1", "全权质量", "Quinn Quality", "QA", "QA_ENGINEER", "qa1@example.com", true, now));
        users.save(new UserInfo("ADMIN1", "亚当管理", "Adam Admin", "IT", "PORTAL_ADMIN", "admin1@example.com", true, now));
    }

    private void seedLinks() {
        // Plain URL links
        Link wip = links.save(new Link("在制品管理", "WIP Management", "https://mes.example.com/wip",
            "factory", "MES", "ACTIVE", 10, true));
        grants.save(new LinkAccessGrant(wip.getId(), GrantType.DEPARTMENT, "FAB1-PROD"));

        // Env-aware link: SPC (DEV/UAT/RELEASE URLs, no single url)
        Link spc = new Link("SPC 分析", "SPC Analysis", null, "line-chart", "QUALITY", "ACTIVE", 20, true);
        spc.setUrlDev("https://spc-dev.example.com");
        spc.setUrlUat("https://spc-uat.example.com");
        spc.setUrlRelease("https://spc.example.com");
        spc = links.save(spc);
        grants.save(new LinkAccessGrant(spc.getId(), GrantType.ROLE, "QA_ENGINEER"));

        // Env-aware link: docs portal (open to everyone)
        Link docs = new Link("帮助文档", "Docs", null, "book", "MES", "ACTIVE", 30, true);
        docs.setUrlDev("https://docs-dev.example.com");
        docs.setUrlUat("https://docs-uat.example.com");
        docs.setUrlRelease("https://docs.example.com");
        links.save(docs);

        // More env-aware demos (open to everyone) across categories/icons
        seedEnvLink("设备监控", "Equipment Monitoring", "gauge", "MES", 40, "mon");
        seedEnvLink("质量看板", "Quality Dashboard", "activity", "QUALITY", 50, "qdash");
        seedEnvLink("维护工单", "Maintenance Orders", "wrench", "MAINTENANCE", 60, "mwo");
    }

    /** Save an env-aware link (DEV/UAT/RELEASE URLs, no single url; no grant = visible to all). */
    private void seedEnvLink(String nameZh, String nameEn, String icon, String category, int sort, String slug) {
        Link l = new Link(nameZh, nameEn, null, icon, category, "ACTIVE", sort, true);
        l.setUrlDev("https://" + slug + "-dev.example.com");
        l.setUrlUat("https://" + slug + "-uat.example.com");
        l.setUrlRelease("https://" + slug + ".example.com");
        links.save(l);
    }
}
