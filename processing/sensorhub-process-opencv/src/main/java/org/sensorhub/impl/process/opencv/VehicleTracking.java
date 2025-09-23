package org.sensorhub.impl.process.opencv;

import org.bytedeco.opencv.opencv_core.Rect;

import java.util.ArrayList;
import java.util.List;

public class VehicleTracking {
    private final int id;                // unique ID for this vehicle
    private final double detectionStart; // first seen timestamp
    private double detectionEnd;         // last seen timestamp
    private boolean active;              // still visible or not

    private Rect lastBoundingBox;        // most recent bounding box
    private final List<Rect> history;    // optional trail of all boxes

    public VehicleTracking(int id, double startTime) {
        this.id = id;
        this.detectionStart = startTime;
        this.detectionEnd = startTime;
        this.active = true;
        this.history = new ArrayList<>();
    }

    // --- Getters ---
    public int getId() {
        return id;
    }

    public double getDetectionStart() {
        return detectionStart;
    }

    public double getDetectionEnd() {
        return detectionEnd;
    }

    public boolean isActive() {
        return active;
    }

    public Rect getLastBoundingBox() {
        return lastBoundingBox;
    }

    public List<Rect> getHistory() {
        return history;
    }

    // --- Tracking logic ---
    public void update(double timestamp, Rect r) {
        this.detectionEnd = timestamp;
//        this.lastBoundingBox = r;
        this.lastBoundingBox = new Rect(r.x(), r.y(), r.width(), r.height());
        System.out.println(
        "lastBox=" + (lastBoundingBox != null ?
                String.format("x=%d,y=%d,w=%d,h=%d",
                        lastBoundingBox.x(),
                        lastBoundingBox.y(),
                        lastBoundingBox.width(),
                        lastBoundingBox.height())
                : "null")
        );
        this.history.add(r); // optional, keeps bbox trail
    }

    public void setDetectionEnd(double timestamp){
        this.detectionEnd = timestamp;
    }
    public void end(double timestamp) {
        this.detectionEnd = timestamp;
        this.active = false;
    }

    @Override
    public String toString() {
        return "VehicleTracking{" +
                "id=" + id +
                ", start=" + detectionStart +
                ", end=" + detectionEnd +
                ", active=" + active +
                ", lastBox=" + (lastBoundingBox != null ?
                String.format("x=%d,y=%d,w=%d,h=%d",
                        lastBoundingBox.x(),
                        lastBoundingBox.y(),
                        lastBoundingBox.width(),
                        lastBoundingBox.height())
                : "null") +
                '}';
    }
}

