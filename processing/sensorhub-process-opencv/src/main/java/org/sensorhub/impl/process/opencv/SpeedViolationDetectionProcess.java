/***************************** BEGIN LICENSE BLOCK ***************************

 Copyright (C) 2022 Botts Innovative Research, Inc. All Rights Reserved.
 ******************************* END LICENSE BLOCK ***************************/
package org.sensorhub.impl.process.opencv;

import net.opengis.swe.v20.*;
import net.opengis.swe.v20.Boolean;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.sensorhub.api.processing.OSHProcessInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.data.AbstractDataComponentImpl;
import org.vast.data.DataArrayImpl;
import org.vast.data.DataBlockByte;
import org.vast.data.DataBlockCompressed;
import org.vast.process.ExecutableProcessImpl;
import org.vast.process.ProcessException;
import org.vast.swe.SWEHelper;
import org.vast.swe.helper.RasterHelper;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import static org.bytedeco.opencv.global.opencv_core.CV_8UC3;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imencode;
import static org.bytedeco.opencv.global.opencv_imgcodecs.IMWRITE_JPEG_QUALITY;

/**
 * Combined process for vehicle detection, tracking, and speed violation detection.
 * Detects vehicles in video frames, tracks them with FOI IDs, monitors velocity
 * during detection periods, and captures JPEG images when violations occur.
 *
 * @author Cardy
 * @since 1.0.0
 */
public class SpeedViolationDetectionProcess extends ExecutableProcessImpl {

    public static final OSHProcessInfo INFO = new OSHProcessInfo(
            "opencv:SpeedViolationDetection",
            "Speed Violation Detection Process",
            "Combined vehicle detection and speed violation detection with JPEG image capture",
            SpeedViolationDetectionProcess.class);

    protected static final Logger logger = LoggerFactory.getLogger(
            SpeedViolationDetectionProcess.class);

    // ==================== INPUTS ====================
    private final Count inputWidth;
    private final Count inputHeight;
    private final DataArray imgIn;
    private final Time inputTimeStamp;

    // Velocity input from OPS241A
    private final Time velocityTimestamp;
    private final Count inputVelocity;

    // ==================== PARAMETERS ====================
    private final Text feature;
    private final Count thresholdParam;

    // ==================== OUTPUTS ====================
    // Speed violation image output
    private final DataArray violationImage;
    private final Time violationTimestamp;
    private final Count violationFoiId;
    private final Count violationWidth;
    private final Count violationHeight;

    // Optional: Vehicle detection metadata output (for monitoring)
    private final Boolean vehicleDetected;
    private final Count numVehicles;
    private final DataArray bboxList;
    private final Time detectionStartTime;
    private final Time detectionEndTime;
    private final Count foiId;

    // ==================== INTERNAL STATE ====================
    private Mat mat;
    private Mat lastProcessedFrame; // Store for violation capture
    private final List<CascadeClassifier> cascadeClassifiers = new ArrayList<>();

    private boolean started = false;
    private int nextId = 1;
    private Map<Integer, VehicleTracking> activeVehicles = new HashMap<>();

    // Track velocity readings per vehicle (FOI ID -> list of velocity readings)
    private static class VelocityReading {
        final double timestamp;
        final double velocity;

        VelocityReading(double timestamp, double velocity) {
            this.timestamp = timestamp;
            this.velocity = Math.abs(velocity); // Store absolute value
        }
    }

    // Time-windowed buffer for velocity readings (keep last 10 seconds)
    private static final double VELOCITY_BUFFER_WINDOW_SECONDS = 10.0;
    private final List<VelocityReading> velocityBuffer = new ArrayList<>();

    private Map<Integer, List<VelocityReading>> vehicleVelocityHistory = new HashMap<>();

    public SpeedViolationDetectionProcess() {
        super(INFO);

        RasterHelper sweFactory = new RasterHelper();
        CVHelper cvHelper = new CVHelper();

        // ==================== INPUTS ====================
        // Video frame input from v4l driver
        inputData.add("imageFrame", sweFactory.createRecord()
                .label("Video Frame")
                .addField("time", inputTimeStamp = sweFactory.createTime()
                        .asSamplingTimeIsoUTC()
                        .build())
                .addField("width", inputWidth = sweFactory.createCount()
                        .id("IN_WIDTH")
                        .label("Input Frame Width")
                        .build())
                .addField("height", inputHeight = sweFactory.createCount()
                        .id("IN_HEIGHT")
                        .label("Input Frame Height")
                        .build())
                .addField("img", imgIn = sweFactory.newRgbImage(
                        inputWidth,
                        inputHeight,
                        DataType.BYTE))
                .build());

        // Velocity input from OPS241A (may arrive at different rate than video)
        inputData.add("velocityInput", sweFactory.createRecord()
                .label("Velocity Input from OPS241A")
                .addField("time", velocityTimestamp = sweFactory.createTime()
                        .asSamplingTimeIsoUTC()
                        .label("Velocity Timestamp")
                        .build())
                .addField("velocity", inputVelocity = sweFactory.createCount()
                        .id("VELOCITY")
                        .label("Vehicle Velocity")
                        .description("Speed reading from OPS241A radar sensor")
                        .build())
                .build());

        // ==================== PARAMETERS ====================
        paramData.add("feature", feature = sweFactory.createText()
                .definition(SWEHelper.getPropertyUri("Feature"))
                .label("Feature")
                .addAllowedValues(FeaturesEnum.class)
                .build());

        paramData.add("thresholdParam", thresholdParam = sweFactory.createCount()
                .label("Speed Threshold")
                .description("Speed limit threshold for violations (same units as velocity input)")
                .build());

        // ==================== OUTPUTS ====================
        // Speed violation image output (main output)
        outputData.add("speedViolationCapture", sweFactory.createRecord()
                .label("Speed Violation Image")
                .addField("captureTime", violationTimestamp = sweFactory.createTime()
                        .asSamplingTimeIsoUTC()
                        .label("Violation Capture Time")
                        .build())
                .addField("width", violationWidth = sweFactory.createCount()
                        .id("IMG_WIDTH")
                        .label("Image Width")
                        .build())
                .addField("height", violationHeight = sweFactory.createCount()
                        .id("IMG_HEIGHT")
                        .label("Image Height")
                        .build())
                .addField("captureImage", violationImage = sweFactory.newRgbImage(
                        violationWidth, violationHeight, DataType.BYTE))
                .build());

        BinaryComponent sampleTimeEnc = sweFactory.newBinaryComponent();
        sampleTimeEnc.setRef("/time");
        sampleTimeEnc.setCdmDataType(DataType.DOUBLE);
        ((AbstractDataComponentImpl)violationTimestamp).setEncodingInfo(sampleTimeEnc);

        BinaryBlock mjpegEncodingOut = sweFactory.newBinaryBlock();
        mjpegEncodingOut.setCompression("JPEG");
        mjpegEncodingOut.setRef("/img");
        ((DataArrayImpl) violationImage).setEncodingInfo(mjpegEncodingOut);



        outputData.add("speedViolatorFoiId", sweFactory.createRecord()
                .label("Violator FOI ID")
                .addField("foiId", violationFoiId = sweFactory.createCount()
                        .id("FOI_ID")
                        .label("FOI ID")
                        .description("Feature of Interest ID of the violating vehicle")
                        .build())
                .build());

        // Optional: Vehicle detection metadata (for monitoring/debugging)
        outputData.add("detectedVehicles", cvHelper.createRecord()
                .label("Detected Vehicles Metadata")
                .addField("numVehicles", numVehicles = sweFactory.createCount()
                        .id("NUM_VEHICLES")
                        .build())
                .addField("bboxList", bboxList = cvHelper.createBboxList(numVehicles)
                        .build())
                .addField("vehicleDetected", vehicleDetected = sweFactory.createBoolean()
                        .id("VEHICLE_DETECTED")
                        .label("Vehicle Detected")
                        .description("Boolean value for vehicle in frame")
                        .build())
                .addField("bboxStartTime", detectionStartTime = sweFactory.createTime()
                        .asSamplingTimeIsoUTC()
                        .id("START_TIME")
                        .label("Detection Start Time")
                        .build())
                .addField("bboxEndTime", detectionEndTime = sweFactory.createTime()
                        .asSamplingTimeIsoUTC()
                        .id("END_TIME")
                        .label("Detection End Time")
                        .build())
                .addField("foiId", foiId = sweFactory.createCount()
                        .label("FOI ID")
                        .id("FOI_ID")
                        .build())
                .build());
    }

    @Override
    public void init() throws ProcessException {
        logger.debug("Initializing SpeedViolationDetectionProcess");

        super.init();

        loadClassifiers();

        started = false;
        nextId = 1;
        activeVehicles.clear();
        vehicleVelocityHistory.clear();

        logger.debug("Initialized");
    }

@Override
public void execute() throws ProcessException {
    logger.debug("Processing event - checking input availability");

    try {
        // Process video frame and detect vehicles
        processVideoFrame();

        // Store current velocity reading in time-windowed buffer
        storeVelocityReading();

        // Check for completed vehicle detections and evaluate violations
        evaluateViolations();

    } catch (Exception e) {
        logger.error("Error during execution", e);
        throw new ProcessException("Error during speed violation detection", e);
    }
}

    private void storeVelocityReading() {

        double velocityTime = velocityTimestamp.getData().getDoubleValue();
        double velocity = inputVelocity.getData().getDoubleValue();

        // Add to buffer
        velocityBuffer.add(new VelocityReading(velocityTime, velocity));

        // Clean up old readings outside the time window
        double currentTime = velocityTime;
        velocityBuffer.removeIf(vr ->
                (currentTime - vr.timestamp) > VELOCITY_BUFFER_WINDOW_SECONDS);

        logger.debug("Stored velocity reading: {} at time {}, buffer size: {}",
                velocity, velocityTime, velocityBuffer.size());

        // Associate velocity with active vehicles
        associateVelocityWithActiveVehicles(velocityTime, velocity);
    }

    private void associateVelocityWithActiveVehicles(double velocityTime, double velocity) {
        // Store velocity reading for all active vehicles within their detection period
        for (VehicleTracking vt : activeVehicles.values()) {
            if (vt.isActive()) {
                double vehicleStart = vt.getDetectionStart();
                double vehicleEnd = vt.getDetectionEnd();

                // Check if velocity reading is within vehicle's detection period
                if (velocityTime >= vehicleStart &&
                        (vehicleEnd == vehicleStart || velocityTime <= vehicleEnd)) {

                    vehicleVelocityHistory.computeIfAbsent(vt.getId(), k -> new ArrayList<>())
                            .add(new VelocityReading(velocityTime, velocity));

                    logger.debug("Associated velocity {} with vehicle FOI ID {} at time {}",
                            velocity, vt.getId(), velocityTime);
                }
            }
        }
    }

    // Updated evaluateViolations to use buffer for ended vehicles
    private void evaluateViolations() {
        if (thresholdParam == null || !thresholdParam.hasData()) {
            logger.debug("Threshold parameter not set, skipping violation evaluation");
            return;
        }

        double threshold = thresholdParam.getData().getDoubleValue();
        List<Integer> vehiclesToRemove = new ArrayList<>();

        // Check all vehicles that have ended
        for (Map.Entry<Integer, VehicleTracking> entry : activeVehicles.entrySet()) {
            int foiId = entry.getKey();
            VehicleTracking vt = entry.getValue();

            if (!vt.isActive()) {
                List<VelocityReading> velocities = vehicleVelocityHistory.get(foiId);

                // If no velocities stored yet, query the buffer for this vehicle's timeframe
                if (velocities == null || velocities.isEmpty()) {
                    velocities = queryVelocityBuffer(vt.getDetectionStart(), vt.getDetectionEnd());
                    if (velocities != null && !velocities.isEmpty()) {
                        vehicleVelocityHistory.put(foiId, velocities);
                    }
                }

                if (velocities != null && !velocities.isEmpty()) {
                    // Find maximum velocity during detection period
                    double maxVelocity = velocities.stream()
                            .mapToDouble(v -> v.velocity)
                            .max()
                            .orElse(0.0);

                    logger.debug("Vehicle FOI ID {}: maxVelocity={}, threshold={}",
                            foiId, maxVelocity, threshold);

                    if (maxVelocity > threshold) {
                        logger.info("SPEED VIOLATION DETECTED: FOI ID={}, maxVelocity={}, threshold={}",
                                foiId, maxVelocity, threshold);
                        captureViolationImage(vt, foiId);
                    }
                } else {
                    logger.debug("No velocity readings found for vehicle FOI ID {} in period [{}, {}]",
                            foiId, vt.getDetectionStart(), vt.getDetectionEnd());
                }

                vehiclesToRemove.add(foiId);
            }
        }

        // Remove ended vehicles from velocity history
        for (Integer foiId : vehiclesToRemove) {
            vehicleVelocityHistory.remove(foiId);
        }
    }

    /**
     * Query velocity buffer for readings within a time range.
     */
    private List<VelocityReading> queryVelocityBuffer(double startTime, double endTime) {
        List<VelocityReading> result = new ArrayList<>();

        for (VelocityReading vr : velocityBuffer) {
            if (vr.timestamp >= startTime && vr.timestamp <= endTime) {
                result.add(vr);
            }
        }

        return result;
    }

    private void processVideoFrame() throws ProcessException {
        double frameTimestamp = inputTimeStamp.getData().getDoubleValue();
        logger.debug("Processing video frame at timestamp: {}", frameTimestamp);

        var imgData = imgIn.getData();

        if (!(imgData instanceof DataBlockByte)) {
            throw new ProcessException("Only DataBlockByte supported as input. Received: " +
                    imgData.getClass().getSimpleName());
        }

            int imgWidth = inputWidth.getData().getIntValue();
            int imgHeight = inputHeight.getData().getIntValue();

            if (imgWidth <= 0 || imgHeight <= 0) {
                throw new ProcessException("Invalid image dimensions: " + imgWidth + "x" + imgHeight);
            }

            byte[] imageFrame = ((DataBlockByte) imgData).getUnderlyingObject();

            if (imageFrame.length != imgWidth * imgHeight * 3) {
                logger.warn("Image frame size {} doesn't match expected size {} for dimensions {}x{}",
                        imageFrame.length, imgWidth * imgHeight * 3, imgWidth, imgHeight);
            }

            BytePointer ptr = null;
            try {
                // Wrap the existing byte array
                ptr = new BytePointer(imageFrame);

                // Create Mat (rows = height, cols = width)
                mat = new Mat(imgHeight, imgWidth, CV_8UC3, ptr);

                // Detect vehicles using SpeedTrapFeatureDetector
                SpeedViolationFeatureDetector detector = new SpeedViolationFeatureDetector(
                        cascadeClassifiers, mat, bboxList, numVehicles, imgWidth, imgHeight,
                        inputTimeStamp, vehicleDetected, detectionStartTime,
                        detectionEndTime, foiId, nextId, activeVehicles, started);

                detector.detectFeatures();

                // Update tracking state
                nextId = detector.getNextId();
                activeVehicles = detector.getActiveVehicles();
                started = detector.getStarted();

                // Store processed frame for potential violation capture
                if (mat != null && !mat.isNull()) {
                    if (lastProcessedFrame != null) {
                        lastProcessedFrame.close();
                    }
                    lastProcessedFrame = mat.clone();
                }

                // Update metadata outputs
                updateVehicleMetadata();

            } catch (Exception e) {
                logger.error("Error processing video frame", e);
                throw new ProcessException("Error processing video frame", e);
            } finally {
                // Note: Don't release mat here - it's used by lastProcessedFrame
                if (ptr != null) {
                    ptr.close();
                }
            }
        }


    private void processVelocityInput() {

        double velocityTime = velocityTimestamp.getData().getDoubleValue();
        double velocity = inputVelocity.getData().getDoubleValue();

        logger.debug("Processing velocity input: {} at timestamp: {}", velocity, velocityTime);

        // Store velocity reading for all active vehicles
        for (Map.Entry<Integer, VehicleTracking> entry : activeVehicles.entrySet()) {
            int foiId = entry.getKey();
            VehicleTracking vt = entry.getValue();

            if (vt.isActive()) {
                double vehicleStart = vt.getDetectionStart();
                double vehicleEnd = vt.getDetectionEnd();

                // Only store if within vehicle's detection period
                if (velocityTime >= vehicleStart && (vehicleEnd == vehicleStart || velocityTime <= vehicleEnd)) {
                    vehicleVelocityHistory.computeIfAbsent(foiId, k -> new ArrayList<>())
                            .add(new VelocityReading(velocityTime, velocity));
                    logger.debug("Stored velocity {} for vehicle FOI ID {} at time {}",
                            velocity, foiId, velocityTime);
                }
            }
        }
    }

//    private void evaluateViolations() {
//
//        double threshold = thresholdParam.getData().getDoubleValue();
//        List<Integer> vehiclesToRemove = new ArrayList<>();
//
//        // Check all vehicles that have ended
//        for (Map.Entry<Integer, VehicleTracking> entry : activeVehicles.entrySet()) {
//            int foiId = entry.getKey();
//            VehicleTracking vt = entry.getValue();
//
//            // Only check vehicles that are no longer active (ended)
//            if (!vt.isActive()) {
//                List<VelocityReading> velocities = vehicleVelocityHistory.get(foiId);
//
//                if (velocities != null && !velocities.isEmpty()) {
//                    // Find maximum velocity during detection period
//                    double maxVelocity = velocities.stream()
//                            .mapToDouble(v -> v.velocity)
//                            .max()
//                            .orElse(0.0);
//
//                    logger.debug("Vehicle FOI ID {}: maxVelocity={}, threshold={}",
//                            foiId, maxVelocity, threshold);
//
//                    if (maxVelocity > threshold) {
//                        logger.info("SPEED VIOLATION DETECTED: FOI ID={}, maxVelocity={}, threshold={}",
//                                foiId, maxVelocity, threshold);
//
//                        // Capture violation image
//                        captureViolationImage(vt, foiId);
//                    }
//                } else {
//                    logger.debug("No velocity readings recorded for vehicle FOI ID {}", foiId);
//                }
//
//                // Clean up velocity history for ended vehicles
//                vehiclesToRemove.add(foiId);
//            }
//        }
//
//        // Remove ended vehicles from velocity history
//        for (Integer foiId : vehiclesToRemove) {
//            vehicleVelocityHistory.remove(foiId);
//        }
//    }

    private void captureViolationImage(VehicleTracking vt, int foiId) {
        if (lastProcessedFrame == null || lastProcessedFrame.isNull()) {
            logger.warn("Cannot capture violation image: no frame available for vehicle {}", foiId);
            return;
        }

        Rect vehicleBox = vt.getLastBoundingBox();
        if (vehicleBox == null) {
            logger.warn("Cannot capture violation image: no bounding box for vehicle {}", foiId);
            return;
        }

        Mat vehicleCrop = null;
        BytePointer buf = null;
        try {
            // Ensure bounding box is within frame bounds
            int x = Math.max(0, vehicleBox.x());
            int y = Math.max(0, vehicleBox.y());
            int width = Math.min(vehicleBox.width(), lastProcessedFrame.cols() - x);
            int height = Math.min(vehicleBox.height(), lastProcessedFrame.rows() - y);

            if (width <= 0 || height <= 0) {
                logger.warn("Invalid bounding box for vehicle {}: x={}, y={}, w={}, h={}",
                        foiId, x, y, width, height);
                return;
            }

            // Extract vehicle region from frame
            Rect safeBox = new Rect(x, y, width, height);
            vehicleCrop = new Mat(lastProcessedFrame, safeBox);

            // Encode as JPEG
            buf = new BytePointer();
            IntPointer params = new IntPointer(2);
            params.put(0, IMWRITE_JPEG_QUALITY);
            params.put(1, 95); // High quality for evidence

            imencode(".jpg", vehicleCrop, buf, params);
            byte[] jpegImage = new byte[(int) buf.limit()];
            buf.get(jpegImage);

            // Publish violation image
            violationImage.getArraySizeComponent().getData().setIntValue(jpegImage.length);

//            violationWidth.getData().setIntValue(width);
//            violationHeight.getData().setIntValue(height);
            violationImage.getData() .setUnderlyingObject(jpegImage);
            violationFoiId.getData().setIntValue(foiId);
            violationTimestamp.getData().setDoubleValue(vt.getDetectionEnd());

            logger.info("Speed violation image captured: FOI ID={}, size={}x{}, jpegSize={} bytes",
                    foiId, width, height, jpegImage.length);

        } catch (Exception e) {
            logger.error("Error capturing speed violation image for vehicle {}", foiId, e);
        } finally {
            if (vehicleCrop != null) {
                vehicleCrop.close();
            }
            if (buf != null) {
                buf.close();
            }
        }
    }

    private void updateVehicleMetadata() {
        // Update vehicle detection metadata outputs for monitoring
        if (activeVehicles.isEmpty()) {
            vehicleDetected.getData().setBooleanValue(false);
            numVehicles.getData().setIntValue(0);
        } else {
            int activeCount = (int) activeVehicles.values().stream()
                    .filter(VehicleTracking::isActive)
                    .count();
            vehicleDetected.getData().setBooleanValue(activeCount > 0);
            numVehicles.getData().setIntValue(activeCount);
        }

        // Update FOI ID and times for the most recently ended vehicle (if any)
        // This is for backward compatibility/monitoring
        VehicleTracking latestEnded = activeVehicles.values().stream()
                .filter(vt -> !vt.isActive())
                .max(Comparator.comparing(VehicleTracking::getDetectionEnd))
                .orElse(null);

        if (latestEnded != null) {
            foiId.getData().setIntValue(latestEnded.getId());
            detectionStartTime.getData().setDoubleValue(latestEnded.getDetectionStart());
            detectionEndTime.getData().setDoubleValue(latestEnded.getDetectionEnd());
        }
    }
//
//    private void loadClassifiers() throws ProcessException {
//        logger.debug("Loading classifiers");
//
//        cascadeClassifiers.clear();
//
//        if (feature == null || !feature.hasData()) {
//            throw new ProcessException(INIT_ERROR_MSG + ": Feature parameter not configured");
//        }
//
//        Object featureData = feature.getData().getUnderlyingObject();
//        if (featureData == null || !(featureData instanceof String[])) {
//            throw new ProcessException(INIT_ERROR_MSG +
//                    ": Feature parameter must be a non-null String array");
//        }
//
//        String[] classifierIds = (String[]) featureData;
//        if (classifierIds.length == 0) {
//            throw new ProcessException(INIT_ERROR_MSG +
//                    ": At least one classifier must be specified");
//        }
//
//        for (String classifierId : classifierIds) {
//            try {
//                FeaturesEnum featureEnum = FeaturesEnum.valueOf(classifierId);
//                URL resourceUrl = getClass().getResource(featureEnum.getResource());
//
//                if (resourceUrl == null) {
//                    throw new ProcessException("Failed to load classifier: " + featureEnum);
//                }
//
//                File resourceFile = Loader.cacheResource(resourceUrl);
//                String path = resourceFile.getAbsolutePath();
//                cascadeClassifiers.add(new CascadeClassifier(path));
//                logger.debug("Loaded classifier: {}", path);
//
//            } catch (IllegalArgumentException e) {
//                throw new ProcessException("Unknown feature type: " + classifierId, e);
//            } catch (IOException e) {
//                throw new ProcessException("Error loading classifier: " + classifierId, e);
//            }
//        }
//
//        if (cascadeClassifiers.isEmpty()) {
//            throw new ProcessException(INIT_ERROR_MSG + ": No classifiers were successfully loaded");
//        }
//
//        logger.debug("Successfully loaded {} classifiers", cascadeClassifiers.size());
//    }

    private void loadClassifiers() {

        logger.debug("Loading classifiers");

        cascadeClassifiers.clear();
        String[] classifierIds = (String[]) feature.getData().getUnderlyingObject();

        for (String classifierId: classifierIds) {
            FeaturesEnum feature = FeaturesEnum.valueOf(classifierId);
            URL resourceUrl = getClass().getResource(feature.getResource());
            File resourceFile;

            try {
                if(resourceUrl != null) {
                    resourceFile = Loader.cacheResource(resourceUrl);
                    String path = resourceFile.getAbsolutePath();
                    cascadeClassifiers.add(new CascadeClassifier(path));
                } else {
                    logger.error("Failed loading classifier, {}", feature);
                }
            } catch (IOException e) {
                logger.error("Exception while loading classifiers, {}", e.toString());
            }
        }
        logger.debug("Classifiers loaded");
    }

    @Override
    public void dispose() {
        logger.debug("Disposing SpeedViolationDetectionProcess");

        // Clean up buffers
        if (velocityBuffer != null) {
            velocityBuffer.clear();
        }

        // Clean up OpenCV native resources
        if (mat != null) {
            mat.close();
            mat = null;
        }

        if (lastProcessedFrame != null) {
            lastProcessedFrame.close();
            lastProcessedFrame = null;
        }

        // Release cascade classifiers
        for (CascadeClassifier classifier : cascadeClassifiers) {
            if (classifier != null) {
                classifier.close();
            }
        }
        cascadeClassifiers.clear();

        // Clear tracking data
        if (activeVehicles != null) {
            activeVehicles.clear();
        }

        if (vehicleVelocityHistory != null) {
            vehicleVelocityHistory.clear();
        }


        super.dispose();
        logger.debug("Disposed");
    }
}