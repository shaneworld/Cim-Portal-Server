package com.cimportal.link;

/** Test helper — lives in the link package so it can call the protected no-arg constructor. */
public final class LinkTestFactory {
    private LinkTestFactory() { }

    public static Link newLink(String code, String categoryCode) {
        Link l = new Link();
        l.setCode(code); l.setNameZh("名"); l.setNameEn("name");
        l.setUrl("https://x"); l.setIcon("factory");
        l.setCategoryCode(categoryCode); l.setStatusCode("ACTIVE");
        l.setSortOrder(1); l.setOpenInNewTab(true);
        return l;
    }
}
