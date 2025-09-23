///***************************** BEGIN LICENSE BLOCK ***************************
//
// Copyright (C) 2022 Botts Innovative Research, Inc. All Rights Reserved.
// ******************************* END LICENSE BLOCK ***************************/
//package org.sensorhub.impl.process.opencv;
//
//import net.opengis.swe.v20.Boolean;
//import net.opengis.swe.v20.Count;
//import net.opengis.swe.v20.DataArray;
//import net.opengis.swe.v20.Time;
//import org.bytedeco.javacv.Java2DFrameConverter;
//import org.bytedeco.opencv.global.opencv_imgproc;
//import org.bytedeco.opencv.opencv_core.*;
//import org.bytedeco.opencv.opencv_core.Point;
//import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import javax.imageio.ImageIO;
//import java.awt.image.*;
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;
//import java.util.List;
//
//import static org.bytedeco.opencv.global.opencv_imgproc.*;
//
///**
// * Performs feature detection according to a set of classifiers provided to the constructor.
// * This class takes in a byte array comprising the raw image data, a text for labeling the image,
// * a set of classifiers, and an output generator.  The image is processed using the classifiers,
// * detected features are bounded by boxes, and the text is used to label the image.
// *
// * @author Nick Garay
// * @since 1.0.0
// */
//class FeatureDetector {
//
//    private final Logger logger = LoggerFactory.getLogger(FeatureDetector.class);
//
//    // Only have a single converter per thread!
//    private final Java2DFrameConverter frameConverter = new Java2DFrameConverter();
//
//    private final Count width;
//    private final Count height;
//
//    private final Mat mat;
//    private final DataArray bboxList;
//    private final Count numVehicles;
//    private final String label;
//    private final Boolean vehicleDetected;
//    private final Time captureTime;
//    private final Time inputTimestamp;
////    private final double timestamp;
//
//    // _____________________________________
//    private final Time startTime;
//    private final Time endTime;
//    private boolean started;
//    private double detectionStart = -1;
//    private double detectionEnd = -1;
//    // _________________________________
//
//
//    private final List<CascadeClassifier> classifiers;
//
//
//    /**
//     * Constructor
//     *
//     * @param classifiers The set of classifiers to use in performing feature detection
//     *                    //     * @param imageData   The raw input image data
//     * @param label       The label, if any, to apply to the image
//     */
//    FeatureDetector(List<CascadeClassifier> classifiers, Mat mat, DataArray bboxList, Count numVehicles, String label, Time inputTimestamp, Boolean vehicleDetected, Time startTime, Time endTime, Time captureTime, Count height, Count width, boolean started) {
//
//        logger.debug("Creating");
//
//        System.setProperty("org.bytedeco.javacpp.logger.debug", "true");
//        System.setProperty("org.bytedeco.javacpp.maxphysicalbytes", "0");
//        System.setProperty("org.bytedeco.javacpp.maxbytes", "0");
//
//        this.classifiers = classifiers;
//
//        this.width = width;
//        this.height = height;
//        this.mat = mat;
//        this.bboxList = bboxList;
//        this.numVehicles = numVehicles;
//        this.label = label;
//        this.inputTimestamp = inputTimestamp;
//        this.vehicleDetected = vehicleDetected;
//        this.startTime = startTime;
//        this.endTime = endTime;
//        this.captureTime = captureTime;
//        this.started = started;
//
//        logger.debug("Created");
//    }
//
//    /**
//     * Creates an image frame with detected features and label applied.
//     *
//     * @return an image frame containing the post-processed image.
//     */
//    byte[] detectFeatures() {
//
//        logger.debug("Detecting features");
//
//        started = vehicleDetected.getValue();
//
//        byte[] outputImageData = null;
//
//        try {
//
//            try (RectVector features = new RectVector()) {
//
//                logger.debug("Applying classifiers");
//
//                for (CascadeClassifier classifier : classifiers) {
//
//                    classifier.detectMultiScale(mat, features);
//
//                    logger.debug("Detecting features");
//
//                    long numberOfVehicles = features.size();
//                    boolean detected = numberOfVehicles > 0;
//
//                    vehicleDetected.getData().setBooleanValue(detected);
//
//                    if (detected) {
//                        if(!started){
//                        detectionStart = inputTimestamp.getData().getDoubleValue();
//                        captureTime.getData().setDoubleValue(detectionStart);
//                        startTime.getData().setDoubleValue(detectionStart);
//                        started = true;
//                        }
////                        detectionEnd = inputTimestamp.getData().getDoubleValue();
//
//                        } else if (started){
//                            detectionEnd = inputTimestamp.getData().getDoubleValue();
//                            endTime.getData().setDoubleValue(detectionEnd);
//                            started = false;
//                        }
////                        detectionEnd = inputTimestamp.getData().getDoubleValue();
//
//
//                        numVehicles.getData().setIntValue((int) numberOfVehicles);
//                        bboxList.updateSize();
//                        var bboxData = bboxList.getData();
//
//                        int idx = 0;
//
//                        for (int i = 0; i < features.size(); i++) {
//
//                            long start = System.currentTimeMillis() / 1000;
////                        startTime.getData().setDoubleValue(start);
//
//                            Rect feature = features.get(i);
//
//                            // Color given as BGR instead of RGB
//                            rectangle(mat, feature, new Scalar(0, 255, 255.0, 1.0));
//
//                            bboxData.setIntValue(idx++, feature.x());
//                            bboxData.setIntValue(idx++, feature.y());
//                            bboxData.setIntValue(idx++, feature.width());
//                            bboxData.setIntValue(idx++, feature.height());
//
//                        }
//
//                        logger.debug("{} features detected", features.size());
//                    }
//
//
////                long end = System.currentTimeMillis() / 1000 + 2;
////                endTime.getData().setDoubleValue(end);
//
////                endTime.getData().setDoubleValue(timestamp);
//                features.deallocate();
//
//            }
//
//            if (null != label) {
//
//                logger.debug("Labeling image");
//
//                Size dimensions = getTextSize(label, FONT_HERSHEY_PLAIN, 2.0, 2, new int[]{1});
//
//                putText(mat, label,
//                        new Point(10, mat.size().height() - dimensions.height() - 1),
//                        FONT_HERSHEY_PLAIN,
//                        1.0,
//                        new Scalar(0.0, 255.0, 255.0, 2.0));
//
//                logger.debug("Image labeled: {}", label);
//            }
//
//            // Convert to BufferedImage AFTER drawing
//            BufferedImage image = matToBufferedImage(mat);
//
////          Encode as JPEG
//            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
//                ImageIO.write(image, "mjpeg", baos);
//                width.getData().setIntValue(mat.cols());
//                height.getData().setIntValue(mat.rows());
//                baos.flush();
//                outputImageData = baos.toByteArray();
//            }
//
//            mat.release();
////            endTime.getData().setDoubleValue(timestamp);
//
//            return outputImageData;
//
//
//        } catch (IOException e) {
//
//            logger.error("Exception while processing event, {}", e.toString());
//        }
//
//        logger.debug("Done detecting features");
//
//        return outputImageData;
//
//    }
//
//    private BufferedImage matToBufferedImage(Mat mat) {
//        int type;
//        Mat convertedMat = new Mat();
//
//        if (mat.channels() == 1) {
//            // Grayscale image — convert to 3-channel grayscale (for BufferedImage.TYPE_3BYTE_BGR)
//            cvtColor(mat, convertedMat, COLOR_GRAY2BGR);
//            type = BufferedImage.TYPE_3BYTE_BGR;
//        } else if (mat.channels() == 3) {
//            // Already BGR — good for TYPE_3BYTE_BGR
//            convertedMat = mat;
//            type = BufferedImage.TYPE_3BYTE_BGR;
//        } else if (mat.channels() == 4) {
//            // BGRA — good for TYPE_4BYTE_ABGR
//            convertedMat = mat;
//            type = BufferedImage.TYPE_4BYTE_ABGR;
//        } else {
//            throw new IllegalArgumentException("Unsupported number of channels: " + mat.channels());
//        }
//
//        int width = convertedMat.cols();
//        int height = convertedMat.rows();
//        byte[] data = new byte[width * height * convertedMat.channels()];
//        convertedMat.data().get(data);
//
//        BufferedImage image = new BufferedImage(width, height, type);
//        image.getRaster().setDataElements(0, 0, width, height, data);
//        return image;
//    }
//
//
//    public static byte[] matToBufferedByteArray(Mat mat) {
//        // Ensure the mat is in the correct format (8-bit, 3 channels, BGR)
//        if (mat.channels() == 1) {
//            // If it's grayscale, convert to 3 channels (BGR)
//            Mat matBGR = new Mat();
//            opencv_imgproc.cvtColor(mat, matBGR, opencv_imgproc.COLOR_BGR2GRAY);
//            mat = matBGR;
//        } else if (mat.channels() == 3) {
//            // Already BGR — good for TYPE_3BYTE_BGR
//            Mat convertedMat = mat;
//            int type = BufferedImage.TYPE_3BYTE_BGR;
//
//        } else {
//            throw new IllegalArgumentException("Unsupported number of channels: " + mat.channels());
//        }
//        // Create a BufferedImage with the same dimensions as the mat
//        int width = mat.cols();
//        int height = mat.rows();
//        int channels = mat.channels();
//        int type = BufferedImage.TYPE_3BYTE_BGR;  // BGR format for OpenCV
//
//        // Create a new BufferedImage
//        BufferedImage image = new BufferedImage(width, height, type);
//
//        // Get the data from the mat and set it into the BufferedImage
//        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
//        mat.data().get(data);
//
//        return data;
//    }
//}
