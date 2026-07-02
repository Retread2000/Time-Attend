package com.example.timeattend;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "employees")
public class Employee {
    @PrimaryKey(autoGenerate = true)
    public int id;    // unique identifier

    public String name;
    public String employeeId;   // employee PIN/login number
    public boolean isClockedIn;

    public Employee(String name, String employeeId) {    // constructor
        this.name = name;
        this.employeeId = employeeId;
        this.isClockedIn = false;
    }
}
