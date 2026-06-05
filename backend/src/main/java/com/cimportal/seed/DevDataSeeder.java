package com.cimportal.seed;

import com.cimportal.enumvalue.EnumCategory;
import com.cimportal.enumvalue.EnumValue;
import com.cimportal.enumvalue.EnumValueRepository;
import com.cimportal.label.Label;
import com.cimportal.label.LabelRepository;
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
@Profile({"dev", "uat"})
public class DevDataSeeder implements ApplicationRunner {

    private final EnumValueRepository enums;
    private final LinkRepository links;
    private final LinkAccessGrantRepository grants;
    private final LabelRepository labels;
    private final UserInfoRepository users;

    public DevDataSeeder(EnumValueRepository enums, LinkRepository links,
                         LinkAccessGrantRepository grants, LabelRepository labels,
                         UserInfoRepository users) {
        this.enums = enums; this.links = links; this.grants = grants;
        this.labels = labels; this.users = users;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (enums.count() == 0) seedEnums();
        if (users.count() == 0) seedUsers();
        if (labels.count() == 0) seedLabels();
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

    private void seedLabels() {
        labels.save(new Label("portal.title", "SYSTEM_NAME", "CIMS 统一门户", "CIMS Portal"));
        labels.save(new Label("nav.dashboard", "UI_TEXT", "仪表盘", "Dashboard"));
        labels.save(new Label("nav.admin", "UI_TEXT", "管理", "Admin"));
    }

    private void seedLinks() {
        Link wip = save("mes-wip", "在制品管理", "WIP Management", "https://mes.example.com/wip", "factory", "MES", "ACTIVE", 10);
        grants.save(new LinkAccessGrant(wip.getId(), GrantType.DEPARTMENT, "FAB1-PROD"));
        Link spc = save("qa-spc", "SPC 分析", "SPC Analysis", "https://spc.example.com", "line-chart", "QUALITY", "ACTIVE", 20);
        grants.save(new LinkAccessGrant(spc.getId(), GrantType.ROLE, "QA_ENGINEER"));
        save("docs", "帮助文档", "Docs", "https://docs.example.com", "book", "MES", "ACTIVE", 30); // no grant → everyone
    }

    private Link save(String code, String zh, String en, String url, String icon,
                      String cat, String status, int sort) {
        Link l = new Link();
        l.setCode(code); l.setNameZh(zh); l.setNameEn(en); l.setUrl(url); l.setIcon(icon);
        l.setCategoryCode(cat); l.setStatusCode(status); l.setSortOrder(sort); l.setOpenInNewTab(true);
        return links.save(l);
    }
}
