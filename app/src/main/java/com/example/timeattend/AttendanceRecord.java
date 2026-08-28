package com.example.timeattend;

import androidx.lifecycle.HasDefaultViewModelProviderFactory;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
    public int recordId;      // primary key
    public int attendanceId;  // link to employee
    public long timestamp;    // time of clock-in/out
    public String type;       // 'in' or 'out'
    public long timeLogged;   // time logged in seconds for the week

    public AttendanceRecord(int attendanceId, long timestamp, String type) {    // constructor
        this.attendanceId = attendanceId;
        this.timestamp = timestamp;
        this.type = type;
    }

    public String getFormattedTime() {  // Format timestamp into 12hr time and date
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - hh:mm a",
                Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
