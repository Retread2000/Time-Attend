package com.example.timeattend;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AttendanceDao {
    // Employee operations
    @Insert
    long insertEmployee(Employee employee); // create new employee

    @Update
    void updateEmployee(Employee employee); // update employee information

    @Query("SELECT * FROM employees")
    List<Employee> getAllEmployees();   // see all employees

    @Query("SELECT * FROM employees WHERE employeeId = :pin LIMIT 1")
    Employee getEmployeeByBadgeId(String pin);  // find employee by PIN/external ID

    @Query("SELECT * FROM employees WHERE id = :id LIMIT 1")
    Employee getEmployeeById(int id);   // find employee by internal ID

    // Attendance operations
    @Insert
    void insertRecord(AttendanceRecord record); // create new attendance record

    @Query("SELECT * FROM attendance_records WHERE attendanceId = :attendId ORDER BY timestamp DESC")
    List<AttendanceRecord> getRecordsForEmployee(int attendId); // see all attendance records for an employee

    @Query("SELECT * FROM attendance_records WHERE attendanceId = :attendId ORDER BY timestamp DESC LIMIT 1")
    AttendanceRecord getLastRecordForEmployee(int attendId);    // see last attendance record for an employee
}
