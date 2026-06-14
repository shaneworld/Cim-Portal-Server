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
        // WIP — plain link, no environment, restricted to FAB1-PROD
        Link wip = links.save(new Link("在制品管理", "WIP Management",
            "https://mes.example.com/wip", "factory", "MES", "ACTIVE", 10, true));
        grants.save(new LinkAccessGrant(wip.getId(), GrantType.DEPARTMENT, "FAB1-PROD"));

        // SPC Analysis — one link per environment (no grant = visible to all QA_ENGINEER holders)
        Link spcDev = makeEnvLink("SPC 分析", "SPC Analysis", "line-chart", "QUALITY", 20, "https://spc-dev.example.com", "DEV");
        spcDev = links.save(spcDev);
        grants.save(new LinkAccessGrant(spcDev.getId(), GrantType.ROLE, "QA_ENGINEER"));

        Link spcUat = makeEnvLink("SPC 分析", "SPC Analysis", "line-chart", "QUALITY", 21, "https://spc-uat.example.com", "UAT");
        spcUat = links.save(spcUat);
        grants.save(new LinkAccessGrant(spcUat.getId(), GrantType.ROLE, "QA_ENGINEER"));

        Link spcRelease = makeEnvLink("SPC 分析", "SPC Analysis", "line-chart", "QUALITY", 22, "https://spc.example.com", "RELEASE");
        spcRelease = links.save(spcRelease);
        grants.save(new LinkAccessGrant(spcRelease.getId(), GrantType.ROLE, "QA_ENGINEER"));

        // Docs portal — plain link (no environment), open to all
        links.save(new Link("帮助文档", "Docs", "https://docs.example.com", "book", "MES", "ACTIVE", 30, true));

        // Equipment Monitoring — plain link, open to all
        links.save(new Link("设备监控", "Equipment Monitoring", "https://mon.example.com", "gauge", "MES", "ACTIVE", 40, true));

        // Maintenance Orders — maintenance status, open to all
        links.save(new Link("维护工单", "Maintenance Orders", "https://mwo.example.com", "wrench", "MAINTENANCE", "MAINTENANCE", 50, true));

        // MES Client — launch link (no grant = visible to all)
        Link mesClient = new Link("MES 客户端", "MES Client", "mesclient://", "factory", "MES", "ACTIVE", 70, true);
        mesClient.setLaunchApp(true);
        mesClient.setDownloadUrl("https://downloads.example.com/mes-client-setup.exe");
        links.save(mesClient);
    }

    private Link makeEnvLink(String nameZh, String nameEn, String icon,
                              String category, int sort, String url, String env) {
        Link l = new Link(nameZh, nameEn, url, icon, category, "ACTIVE", sort, true);
        l.setEnvironment(env);
        return l;
    }
}
