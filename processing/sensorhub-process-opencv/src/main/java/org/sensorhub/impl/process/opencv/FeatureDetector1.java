/***************************** BEGIN LICENSE BLOCK ***************************

 Copyright (C) 2022 Botts Innovative Research, Inc. All Rights Reserved.
 ******************************* END LICENSE BLOCK ***************************/
package org.sensorhub.impl.process.opencv;

import net.opengis.swe.v20.Boolean;
import net.opengis.swe.v20.Count;
import net.opengis.swe.v20.DataArray;
import net.opengis.swe.v20.Time;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * Performs feature detection according to a set of classifiers provided to the constructor.
 * This class takes in a byte array comprising the raw image data, a text for labeling the image,
 * a set of classifiers, and an output generator.  The image is processed using the classifiers,
 * detected features are bounded by boxes, and the text is used to label the image.
 *
 * @author Nick Garay
 * @since 1.0.0
 */
class FeatureDetector1 {

    private final Logger logger = LoggerFactory.getLogger(FeatureDetector1.class);

    // Only have a single converter per thread!
    private final Java2DFrameConverter frameConverter = new Java2DFrameConverter();

    private final Count width;
    private final Count height;

    private final Mat mat;
    private final DataArray bboxList;
    private final Count numVehicles;
    private final String label;
    private final Boolean vehicleDetected;
//    private final Time captureTime;
    private final Time inputTimestamp;
//    private final double timestamp;

    // _____________________________________
    private final Time startTime;
    private final Time endTime;
    private boolean started;
//    private double detectionStart = -1;
//    private double detectionEnd = -1;
    private final Count foiId;
    // _________________________________

    private final int imgWidth;
    private final int imgHeight;
    private Map<Integer, VehicleTracking> activeVehicles = new HashMap<>();
    private int nextId;


    private final List<CascadeClassifier> classifiers;


    /**
     * Constructor
     *
     * @param classifiers The set of classifiers to use in performing feature detection
     *                    //     * @param imageData   The raw input image data
     * @param label       The label, if any, to apply to the image
     */
    FeatureDetector1(List<CascadeClassifier> classifiers,
                     Mat mat,
                     DataArray bboxList,
                     Count numVehicles,
                     int imgWidth,
                     int imgHeight,
                     String label,
                     Time inputTimestamp,
                     Boolean vehicleDetected,
                     Time startTime,
                     Time endTime,
                     Time captureTime,
                     Count height,
                     Count width,
                     Count foiId,
                     int nextId,
                     Map<Integer, VehicleTracking> activeVehicles) {

        logger.debug("Creating");

        System.setProperty("org.bytedeco.javacpp.logger.debug", "true");
        System.setProperty("org.bytedeco.javacpp.maxphysicalbytes", "0");
        System.setProperty("org.bytedeco.javacpp.maxbytes", "0");

        this.classifiers = classifiers;

        this.width = width;
        this.height = height;
        this.mat = mat.clone();
        this.bboxList = bboxList;
        this.numVehicles = numVehicles;
        this.imgWidth = imgWidth;
        this.imgHeight = imgHeight;
        this.label = label;
        this.inputTimestamp = inputTimestamp;
        this.vehicleDetected = vehicleDetected;
        this.startTime = startTime;
        this.endTime = endTime;
//        this.captureTime = captureTime;
        this.foiId = foiId;
        this.nextId = nextId;
        this.activeVehicles = activeVehicles;

        logger.debug("Created");
    }

    /**
     * Creates an image frame with detected features and label applied.
     *
     * @return an image frame containing the post-processed image.
     */
    byte[] detectFeatures() {

        logger.debug("Detecting features");

        started = vehicleDetected.getValue();

        byte[] outputImageData = null;

        try {

            try (RectVector features = new RectVector()) {

                logger.debug("Applying classifiers");

                for (CascadeClassifier classifier : classifiers) {

//                    classifier.detectMultiScale(mat, features);
//                    Mat resized = new Mat();
//                    opencv_imgproc.resize(mat, resized, new Size(mat.cols() * 2, mat.rows() * 2)); // upscale 2x
                    classifier.detectMultiScale(
                            mat, features,
                            1.05, 3, 0,
                            new Size(mat.cols() / 6, mat.rows() / 6),
                            new Size(mat.cols(), mat.rows())
                    );

                    logger.debug("Detecting features");

                    long numberOfVehicles = features.size();
                    boolean detected = numberOfVehicles > 0;

                    vehicleDetected.getData().setBooleanValue(detected);

                    // Tracks which VehicleTracking IDs were updated in the current frame
                    Set<Integer> matchedThisFrame = new HashSet<>();

                    if (detected) {
                        if (!started) {
                            started = true;
                        }

                    numVehicles.getData().setIntValue((int) numberOfVehicles);
                    bboxList.updateSize();
                    var bboxData = bboxList.getData();

                    int idx = 0;
//                    Mat safeMat = mat.clone();
                    for (int i = 0; i < features.size(); i++) {

                        Rect feature = features.get(i);
                        Rect match = new Rect(feature.x(), feature.y(), feature.width(), feature.height());

                        bboxData.setIntValue(idx++, feature.x());
                        bboxData.setIntValue(idx++, feature.y());
                        bboxData.setIntValue(idx++, feature.width());
                        bboxData.setIntValue(idx++, feature.height());

                        System.out.println(
                                "featureBbox=" + String.format("x=%d,y=%d,w=%d,h=%d",
                                                feature.x()/2,
                                                feature.y()/2,
                                                feature.width()/2,
                                                feature.height()/2)
                        );

//                        rectangle(mat, match, new Scalar(0, 255, 255, 1.0));
                        // Color given as BGR instead of RGB
                        rectangle(mat, feature, new Scalar(0, 255, 255.0, 1.0));

//                        safeMat.release();

                        int vehicleId = matchToExistingTracker(match, imgWidth, imgHeight);

                        if (vehicleId == -1) {
                            // New vehicle
                            double now = inputTimestamp.getData().getDoubleValue();
                            VehicleTracking vt = new VehicleTracking(nextId++, now);
                            vt.update(now, match);
                            activeVehicles.put(vt.getId(), vt);

//                            startTime.getData().setDoubleValue(vt.getDetectionStart());
//                            foiId.getData().setIntValue(vt.getId()); // optional FOI ID publishing
                            matchedThisFrame.add(vt.getId());

                            setNextId(this.nextId);

                        } else {
                            // Update existing vehicle
                            double now = inputTimestamp.getData().getDoubleValue();
                            VehicleTracking vt = activeVehicles.get(vehicleId);
                            if (vt.isActive()) {
                                vt.update(now, match);
                                matchedThisFrame.add(vehicleId);
                            }
                        }
                        }
                    }
                    // If no detections, mark all active trackers as ended
                    if (!detected && started) {
                        for (VehicleTracking vt : activeVehicles.values()) {
                            if (vt.isActive()) {
                                double end = inputTimestamp.getData().getDoubleValue();
                                vt.end(end);
                                startTime.getData().setDoubleValue(vt.getDetectionStart());
                                endTime.getData().setDoubleValue(vt.getDetectionEnd());
                                foiId.getData().setIntValue(vt.getId());
                            }
                        }
                            started = false;
                    // If detections exist but some trackers weren’t matched, update their detectionEnd
                    } else {
                        for (VehicleTracking vt : activeVehicles.values()) {
                            if (vt.isActive() && !matchedThisFrame.contains(vt.getId())) {
                                double end = inputTimestamp.getData().getDoubleValue();
                                vt.setDetectionEnd(end);
                            }
                        }
                    }
                    setActiveVechicles(this.activeVehicles);
                    logger.debug("{} features detected", features.size());
                }

                features.deallocate();
            }
//            Mat frameMat = mat.clone();
            if (null != label) {

                logger.debug("Labeling image");

                Size dimensions = getTextSize(label, FONT_HERSHEY_PLAIN, 2.0, 2, new int[]{1});

                putText(mat, label + " /  FoiID: " + foiId.getValue(),
                        new Point(10, mat.size().height() - dimensions.height() - 1),
                        FONT_HERSHEY_PLAIN,
                        1.0,
                        new Scalar(0.0, 255.0, 255.0, 2.0));

                logger.debug("Image labeled: {},{}", label, foiId);

//                frameMat.release();
            }

//            Mat imgMat = mat.clone();
            // Convert to BufferedImage AFTER drawing
            BufferedImage image = matToBufferedImage(mat);

//          Encode as JPEG
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ImageIO.write(image, "jpeg", baos);
                width.getData().setIntValue(mat.cols());
                height.getData().setIntValue(mat.rows());
                baos.flush();
                outputImageData = baos.toByteArray();
            }

//            imgMat.release();
            mat.release();

            return outputImageData;


        } catch (IOException e) {

            logger.error("Exception while processing event, {}", e.toString());
        }

        logger.debug("Done detecting features");

        return outputImageData;

    }

    private int matchToExistingTracker(Rect feature, int frameWidth, int frameHeight) {
        int matchedId = -1;
        double bestScore = 0.0;

        // Normalize distance against the frame diagonal
        double frameDiag = Math.sqrt(frameWidth * frameWidth + frameHeight * frameHeight);

        for (VehicleTracking vt : activeVehicles.values()) {
            if (vt.isActive()) {
                Rect lastBox = vt.getLastBoundingBox();
                if (lastBox == null) continue;

                // 1. Compute IoU
                double iou = computeIoU(feature, lastBox);

                // 2. Compute normalized center distance
                double distance = centerDistance(feature, lastBox);
                double normDistance = distance / frameDiag; // 0.0 = same center, 1.0 = farthest apart

                // Hybrid scoring:
                // - Weighted IoU + inverse distance
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

    private double computeIoU(Rect a, Rect b) {
        int x1 = Math.max(a.x(), b.x());
        int y1 = Math.max(a.y(), b.y());
        System.out.println();
        System.out.println(String.valueOf((a.x())));
        System.out.println(String.valueOf((a.y())));
        System.out.println(String.valueOf((a.height())));
        System.out.println(String.valueOf((a.width())));
        System.out.println(String.valueOf((b.x())));
        System.out.println(String.valueOf((b.y())));
        System.out.println(String.valueOf((b.width())));
        System.out.println(String.valueOf((b.height())));
        int x2 = Math.min(a.x() + a.width(), b.x() + b.width());
        int y2 = Math.min(a.y() + a.height(), b.y() + b.height());

        int intersectionArea = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        int unionArea = a.width() * a.height() + b.width() * b.height() - intersectionArea;

        return unionArea > 0 ? (double) intersectionArea / unionArea : 0.0;
    }

    private double centerDistance(Rect a, Rect b) {
        double ax = a.x() + a.width() / 2.0;
        double ay = a.y() + a.height() / 2.0;
        double bx = b.x() + b.width() / 2.0;
        double by = b.y() + b.height() / 2.0;

        return Math.hypot(ax - bx, ay - by);
    }

    public int setNextId(int nextId){
        return nextId;
    }

    public int getNextId(){
        return nextId;
    }

    public Map<Integer, VehicleTracking> setActiveVechicles(Map<Integer, VehicleTracking> activeVehicles){
        return activeVehicles;
    }

    public Map<Integer, VehicleTracking> getActiveVehicles(){
        return activeVehicles;
    }


    public static List<Rect> toList(RectVector rectVector) {
        List<Rect> list = new ArrayList<>((int) rectVector.size());
        for (long i = 0; i < rectVector.size(); i++) {
            Rect r = rectVector.get(i);
            // Clone to avoid weird native reference issues
            list.add(new Rect(r.x(), r.y(), r.width(), r.height()));
        }
        return list;
    }

    //                    List<Rect> boxes = toList(features);
//                    for (Rect box : boxes) {
//                        System.out.printf("Box: x=%d y=%d w=%d h=%d%n",
//                                box.x(), box.y(), box.width(), box.height());
//                    }


    private BufferedImage matToBufferedImage(Mat mat) {
        int type;
        Mat convertedMat = new Mat();

        if (mat.channels() == 1) {
            // Grayscale image — convert to 3-channel grayscale (for BufferedImage.TYPE_3BYTE_BGR)
            cvtColor(mat, convertedMat, COLOR_GRAY2BGR);
            type = BufferedImage.TYPE_3BYTE_BGR;
        } else if (mat.channels() == 3) {
            // Already BGR — good for TYPE_3BYTE_BGR
            convertedMat = mat;
            type = BufferedImage.TYPE_3BYTE_BGR;
        } else if (mat.channels() == 4) {
            // BGRA — good for TYPE_4BYTE_ABGR
            convertedMat = mat;
            type = BufferedImage.TYPE_4BYTE_ABGR;
        } else {
            throw new IllegalArgumentException("Unsupported number of channels: " + mat.channels());
        }

        int width = convertedMat.cols();
        int height = convertedMat.rows();
        byte[] data = new byte[width * height * convertedMat.channels()];
        convertedMat.data().get(data);

        BufferedImage image = new BufferedImage(width, height, type);
        image.getRaster().setDataElements(0, 0, width, height, data);
        return image;
    }


    public static byte[] matToBufferedByteArray(Mat mat) {
        // Ensure the mat is in the correct format (8-bit, 3 channels, BGR)
        if (mat.channels() == 1) {
            // If it's grayscale, convert to 3 channels (BGR)
            Mat matBGR = new Mat();
            opencv_imgproc.cvtColor(mat, matBGR, opencv_imgproc.COLOR_BGR2GRAY);
            mat = matBGR;
        } else if (mat.channels() == 3) {
            // Already BGR — good for TYPE_3BYTE_BGR
            Mat convertedMat = mat;
            int type = BufferedImage.TYPE_3BYTE_BGR;

        } else {
            throw new IllegalArgumentException("Unsupported number of channels: " + mat.channels());
        }
        // Create a BufferedImage with the same dimensions as the mat
        int width = mat.cols();
        int height = mat.rows();
        int channels = mat.channels();
        int type = BufferedImage.TYPE_3BYTE_BGR;  // BGR format for OpenCV

        // Create a new BufferedImage
        BufferedImage image = new BufferedImage(width, height, type);

        // Get the data from the mat and set it into the BufferedImage
        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        mat.data().get(data);

        return data;
    }
}
