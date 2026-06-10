package com.cimportal.portal.dto;

import java.util.List;

public record HomeCategory(String categoryCode, String categoryLabelZh,
                           String categoryLabelEn, List<HomeLink> links) { }
