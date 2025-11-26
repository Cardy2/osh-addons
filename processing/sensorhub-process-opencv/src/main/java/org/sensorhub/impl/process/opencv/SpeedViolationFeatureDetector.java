/***************************** BEGIN LICENSE BLOCK ***************************

 Copyright (C) 2022 Botts Innovative Research, Inc. All Rights Reserved.
 ******************************* END LICENSE BLOCK ***************************/
package org.sensorhub.impl.process.opencv;

import net.opengis.swe.v20.Boolean;
import net.opengis.swe.v20.Count;
import net.opengis.swe.v20.DataArray;
import net.opengis.swe.v20.Time;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * Performs vehicle detection and tracking using cascade classifiers.
 * Detects vehicles in frames, tracks them with unique FOI IDs, and maintains
 * detection start/end times for each vehicle.
 *
 * @author Cardy
 * @since 1.0.0
 */
class SpeedViolationFeatureDetector {

    private final Logger logger = LoggerFactory.getLogger(SpeedViolationFeatureDetector.class);

    private final Mat mat;
    private final DataArray bboxList;
    private final Count numVehicles;
    private final Boolean vehicleDetected;
    private final Time inputTimestamp;
    private final Time startTime;
    private final Time endTime;
    private final Count foiId;

    private final int imgWidth;
    private final int imgHeight;
    private Map<Integer, VehicleTracking> activeVehicles;
    private int nextId;
    private boolean started;

    private final List<CascadeClassifier> classifiers;

    /**
     * Constructor
     *
     * @param classifiers The set of classifiers to use for vehicle detection
     * @param mat The input image Mat to process
     * @param bboxList Output data array for bounding boxes
     * @param numVehicles Output count for number of vehicles
     * @param imgWidth Image width in pixels
     * @param imgHeight Image height in pixels
     * @param inputTimestamp Current frame timestamp
     * @param vehicleDetected Output boolean indicating if vehicle is detected
     * @param startTime Output time for detection start (set when vehicle ends)
     * @param endTime Output time for detection end (set when vehicle ends)
     * @param foiId Output FOI ID (set when vehicle ends)
     * @param nextId Next available vehicle ID
     * @param activeVehicles Map of active vehicle trackers (will be modified)
     * @param started Whether detection has started
     */
    SpeedViolationFeatureDetector(List<CascadeClassifier> classifiers,
                                  Mat mat,
                                  DataArray bboxList,
                                  Count numVehicles,
                                  int imgWidth,
                                  int imgHeight,
                                  Time inputTimestamp,
                                  Boolean vehicleDetected,
                                  Time startTime,
                                  Time endTime,
                                  Count foiId,
                                  int nextId,
                                  Map<Integer, VehicleTracking> activeVehicles,
                                  boolean started) {

        System.setProperty("org.bytedeco.javacpp.maxphysicalbytes", "0");
        System.setProperty("org.bytedeco.javacpp.maxbytes", "0");

        this.classifiers = classifiers;
        this.mat = mat;
        this.bboxList = bboxList;
        this.numVehicles = numVehicles;
        this.imgWidth = imgWidth;
        this.imgHeight = imgHeight;
        this.inputTimestamp = inputTimestamp;
        this.vehicleDetected = vehicleDetected;
        this.startTime = startTime;
        this.endTime = endTime;
        this.foiId = foiId;
        this.nextId = nextId;
        this.activeVehicles = activeVehicles;
        this.started = started;
    }

    /**
     * Detects vehicles in the current frame and updates tracking state.
     * Draws bounding boxes on the Mat and updates vehicle tracking information.
     */
    void detectFeatures() {
        logDebug("Detecting features - started= " + started + ", activeVehicles= " + activeVehicles.size());

        try (RectVector features = new RectVector()) {

            // Convert to grayscale for detection
            Mat gray = new Mat();
            try {
                cvtColor(mat, gray, COLOR_BGR2GRAY);
                equalizeHist(gray, gray);

                // Apply each classifier
                for (CascadeClassifier classifier : classifiers) {
                    features.clear();

                    classifier.detectMultiScale(
                            gray, features,
                            1.05, 3, 0,
                            new Size(mat.cols() / 6, mat.rows() / 6),
                            new Size(mat.cols(), mat.rows())
                    );

                    long numberOfVehicles = features.size();
                    boolean detected = numberOfVehicles > 0;

                    vehicleDetected.getData().setBooleanValue(detected);

                    // Tracks which VehicleTracking IDs were matched in the current frame
                    Set<Integer> matchedThisFrame = new HashSet<>();

                    if (detected) {
                        if (!started) {
                            started = true;
                            logger.info("TRANSITION: started=true (first vehicle detected)");
                        }
                        logDebug("Detection result: detected= " + detected + ", numberOfVehicles= " +
                                numberOfVehicles + "}, started= " + started);

                        numVehicles.getData().setIntValue((int) numberOfVehicles);
                        bboxList.updateSize();
                        var bboxData = bboxList.getData();

                        int idx = 0;

                        for (int i = 0; i < features.size(); i++) {
                            Rect feature = features.get(i);

                            // Store bounding box coordinates
                            bboxData.setIntValue(idx++, feature.x());
                            bboxData.setIntValue(idx++, feature.y());
                            bboxData.setIntValue(idx++, feature.width());
                            bboxData.setIntValue(idx++, feature.height());

                            // Draw bounding box on image (cyan color)
                            rectangle(mat, feature, new Scalar(0, 255, 255, 1.0));

                            // Match to existing tracker or create new one
                            int vehicleId = matchToExistingTracker(feature, imgWidth, imgHeight);

                            if (vehicleId == -1) {
                                // New vehicle - create new tracker
                                double now = inputTimestamp.getData().getDoubleValue();
                                VehicleTracking vt = new VehicleTracking(nextId++, now);
                                vt.update(now, feature);
                                activeVehicles.put(vt.getId(), vt);
                                matchedThisFrame.add(vt.getId());
                                logDebug("Created new vehicle tracker: FOI ID= " + vt.getId());

                            } else {
                                // Update existing vehicle tracker
                                double now = inputTimestamp.getData().getDoubleValue();
                                VehicleTracking vt = activeVehicles.get(vehicleId);
                                if (vt.isActive()) {
                                    vt.update(now, feature);
                                    matchedThisFrame.add(vehicleId);
                                    logDebug("Updated vehicle tracker: FOI ID= " + vehicleId);
                                }
                            }
                        }
                    }

                    // Handle vehicles that ended (no longer detected)
                    if (!detected && started) {
                        // No detections - end all active trackers
                        for (VehicleTracking vt : activeVehicles.values()) {
                            if (vt.isActive()) {
                                double end = inputTimestamp.getData().getDoubleValue();
                                vt.end(end);
                                startTime.getData().setDoubleValue(vt.getDetectionStart());
                                endTime.getData().setDoubleValue(vt.getDetectionEnd());
                                foiId.getData().setIntValue(vt.getId());
                                logDebug("Ended vehicle tracker: FOI ID= " + vt.getId() +
                                        ", duration= " + (vt.getDetectionEnd() - vt.getDetectionStart()) + "s");
                            }
                        }
                        logger.info("TRANSITION: started=false (all vehicles disappeared)");
                        started = false;

                    } else if (detected && started) {
                        // Detections exist but some trackers weren't matched - they disappeared
                        for (VehicleTracking vt : activeVehicles.values()) {
                            if (vt.isActive() && !matchedThisFrame.contains(vt.getId())) {
                                double end = inputTimestamp.getData().getDoubleValue();
                                vt.end(end);
                                startTime.getData().setDoubleValue(vt.getDetectionStart());
                                endTime.getData().setDoubleValue(vt.getDetectionEnd());
                                foiId.getData().setIntValue(vt.getId());
                                logDebug("Ended unmatched vehicle tracker: FOI ID= " + vt.getId()
                                        + ", duration= " + (vt.getDetectionEnd() - vt.getDetectionStart()) + "s ");
                            }
                        }

                        logDebug("Features detected: " + features.size() + ", active vehicles: " + activeVehicles.size());
                    }
                }

                } finally{
                    gray.close();
                }

            } catch (Exception e) {
                logError("Exception while detecting features", e);
                throw new RuntimeException("Error during feature detection", e);
            }

        }

    /**
     * Matches a detected feature to an existing vehicle tracker based on IoU and distance.
     *
     * @param feature The detected bounding box
     * @param frameWidth Frame width for normalization
     * @param frameHeight Frame height for normalization
     * @return The matched vehicle ID, or -1 if no match found
     */
    private int matchToExistingTracker(Rect feature, int frameWidth, int frameHeight) {
        int matchedId = -1;
        double bestScore = 0.0;

        // Normalize distance against the frame diagonal
        double frameDiag = Math.sqrt(frameWidth * frameWidth + frameHeight * frameHeight);

        for (VehicleTracking vt : activeVehicles.values()) {
            if (vt.isActive()) {
                Rect lastBox = vt.getLastBoundingBox();
                if (lastBox == null) continue;

                // Compute IoU (Intersection over Union)
                double iou = computeIoU(feature, lastBox);

                // Compute normalized center distance
                double distance = centerDistance(feature, lastBox);
                double normDistance = distance / frameDiag; // 0.0 = same center, 1.0 = farthest apart

                // Hybrid scoring: weighted IoU + inverse distance
                double score = 0.6 * iou + 0.4 * (1.0 - Math.min(normDistance, 1.0));

                // Apply minimum conditions (loose filter before scoring)
                if (iou > 0.3 || (iou > 0.15 && distance < 0.1 * frameDiag)) {
                    if (score > bestScore) {
                        bestScore = score;
                        matchedId = vt.getId();
                    }
                }
            }
        }

        return matchedId;
    }

    /**
     * Computes Intersection over Union (IoU) of two rectangles.
     */
    private double computeIoU(Rect a, Rect b) {
        int x1 = Math.max(a.x(), b.x());
        int y1 = Math.max(a.y(), b.y());
        int x2 = Math.min(a.x() + a.width(), b.x() + b.width());
        int y2 = Math.min(a.y() + a.height(), b.y() + b.height());

        int intersectionArea = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        int unionArea = a.width() * a.height() + b.width() * b.height() - intersectionArea;

        return unionArea > 0 ? (double) intersectionArea / unionArea : 0.0;
    }

    /**
     * Computes the Euclidean distance between centers of two rectangles.
     */
    private double centerDistance(Rect a, Rect b) {
        double ax = a.x() + a.width() / 2.0;
        double ay = a.y() + a.height() / 2.0;
        double bx = b.x() + b.width() / 2.0;
        double by = b.y() + b.height() / 2.0;

        return Math.hypot(ax - bx, ay - by);
    }

    // ==================== Getters ====================

    public int getNextId() {
        return nextId;
    }

    public Map<Integer, VehicleTracking> getActiveVehicles() {
        return activeVehicles;
    }

    public boolean getStarted() {
        return started;
    }

    private void logDebug(String message) {
        // Use System.out since loggers aren't working
        System.out.println("[DEBUG] " + message);
    }

    private void logError(String message, Throwable e) {
        System.err.println("[ERROR] " + message);
        if (e != null) {
            e.printStackTrace();
        }
    }

}