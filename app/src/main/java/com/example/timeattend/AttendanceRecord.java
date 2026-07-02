package com.example.timeattend;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "attendance_records",
        foreignKeys = @ForeignKey(
                entity = Employee.class,
                parentColumns = "id",           // 'id' from Employee table
                childColumns = "attendanceId",  // matching id column for employee in this table
                onDelete = ForeignKey.CASCADE   // delete record if employee is deleted
        )
)
public class AttendanceRecord {
    @PrimaryKey(autoGenerate = true)
    public int recordId;

    public int attendanceId;  // link to employee
    public long timestamp;    // time of clock-in/out
    public String type;       // 'in' or 'out'

    public AttendanceRecord(int attendanceId, long timestamp, String type) {    // constructor
        this.attendanceId = attendanceId;
        this.timestamp = timestamp;
        this.type = type;
    }
}
