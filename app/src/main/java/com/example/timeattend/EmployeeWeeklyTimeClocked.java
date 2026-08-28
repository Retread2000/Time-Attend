package com.example.timeattend;

public class EmployeeWeeklyTimeClocked {
    public String employeeName;
    public long totalSeconds;

    public EmployeeWeeklyTimeClocked(String employeeName, long totalSeconds) {
        this.employeeName = employeeName;
        this.totalSeconds = totalSeconds;
    }
}
