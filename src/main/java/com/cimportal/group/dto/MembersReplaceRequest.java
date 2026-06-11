package com.cimportal.group.dto;

import java.util.List;

public record MembersReplaceRequest(List<String> employeeIds) {
    public List<String> safe() { return employeeIds == null ? List.of() : employeeIds; }
}
