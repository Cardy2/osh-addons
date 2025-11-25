/***************************** BEGIN LICENSE BLOCK ***************************

 Copyright (C) 2022 Botts Innovative Research, Inc. All Rights Reserved.
 ******************************* END LICENSE BLOCK ***************************/
package org.sensorhub.impl.process.opencv;

import net.opengis.swe.v20.Boolean;
import net.opengis.swe.v20.*;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.sensorhub.api.processing.OSHProcessInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.text.SimpleDateFormat;
import java.util.*;

import static org.bytedeco.opencv.global.opencv_core.CV_8UC3;

/**
 * Process for performing feature detection on images from a video source
 *
 * @author Cardy
 * @since 1.0.0
 */
public class FeatureDetectionProcess1 extends ExecutableProcessImpl {

    public static final OSHProcessInfo INFO = new OSHProcessInfo(
            "opencv:FeatureDetect",
            "Feature Detection Algorithm",
            "Multi feature detection algorithm, can be used to detect features in video frames",
            FeatureDetectionProcess1.class);

    protected static final Logger logger = LoggerFactory.getLogger(
            FeatureDetectionProcess1.class);

    private final Count inputWidth;
    private final Count inputHeight;
    private final Count outputWidth;
    private final Count outputHeight;
    private final DataArray imgIn;
    private final DataArray imgOut;
    private final Time inputTimeStamp;
    private final Time outputTimeStamp;
    private final Text feature;
    private Mat mat;
    private final DataArray bboxList;
    private final Count numVehicles;
    private final Time detectionStartTime;
    private final Time detectionEndTime;
    private final Boolean vehicleDetected;
    private final Time captureTimestamp;
    private final Count foiId;

    private boolean started;

    private Map<Integer, VehicleTracking> activeVehicles = new HashMap<>();
    private int nextId;



    // _________________________________________
    private final Boolean overThresholdInput;
//    private boolean overThreshold;
    //________________________________________

    private final List<CascadeClassifier> cascadeClassifiers = new ArrayList<>();

    public FeatureDetectionProcess1() {

        super(INFO);

        // Get an instance of SWE Factory suitable to build components
        RasterHelper sweFactory = new RasterHelper();

        // Inputs
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

//        BinaryBlock mjpegEncoding = sweFactory.newBinaryBlock();
//        mjpegEncoding.setCompression("MJPEG");
//        ((DataArrayImpl) imgIn).setEncodingInfo(mjpegEncoding);

        inputData.add("threshold", sweFactory.createRecord()
                .label("Threshold")
                .addField("overThreshold", overThresholdInput = sweFactory.createBoolean()
                        .label("Vehicle has exceeded threshold")
                        .build())
                .build());

        // Parameters
        paramData.add("feature", feature = sweFactory.createText()
                .definition(SWEHelper.getPropertyUri("Feature"))
                .label("Feature")
                .addAllowedValues(FeaturesEnum.class)
                .build());

        // Outputs
        outputData.add("rgbFrame", sweFactory.createRecord()
                .label("Video Frame")
                .addField("time", outputTimeStamp = sweFactory.createTime()
                        .asSamplingTimeIsoUTC()
                        .build())
                .addField("width", outputWidth = sweFactory.createCount()
                        .id("OUT_WIDTH")
                        .label("Output Frame Width")
                        .build())
                .addField("height", outputHeight = sweFactory.createCount()
                        .id("OUT_HEIGHT")
                        .label("Output Frame Height")
                        .build())
                .addField("img", imgOut = sweFactory.newRgbImage(
                        outputWidth,
                        outputHeight,
                        DataType.BYTE))
                .build());

        BinaryBlock jpegEncoding = sweFactory.newBinaryBlock();
        jpegEncoding.setCompression("MJPEG");
        ((DataArrayImpl) imgOut).setEncodingInfo(jpegEncoding);

        CVHelper swe = new CVHelper();


        outputData.add("detectedVehicles", swe.createRecord()
                .label("Detected Vehicles")
                .addField("numVehicles", numVehicles = swe.createCount()
                        .id("NUM_VEHICLES")
                        .build())
                .addField("bboxList", bboxList = swe.createBboxList(numVehicles)
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
                .addField("captureTimestamp", captureTimestamp = sweFactory.createTime()
                        .asSamplingTimeIsoUTC()
                        .id("VEHICLE_TIME")
                        .label("Time vehicle is captured in img")
                        .build())
                .addField("foiId", foiId = sweFactory.createCount()
                        .label("FOI ID")
                        .id("FOI_ID")
                        .build())
                .build());
    }


    @Override
    public void init() throws ProcessException {

        logger.debug("Initializing");

        super.init();

        loadClassifiers();

        started = false;
        nextId = 1;

        logger.debug("Initialized");
    }



    @Override
    public void execute() {

        logger.debug("Processing event");

        if (nextId != 1) {
            nextId = getNextId();
            logger.debug("NextID = {}", nextId);
        }
        activeVehicles = getActiveVehicles();
        logger.debug("Active vehicles = {}", activeVehicles.values());

        double timeStampLabel = inputTimeStamp.getValue().getAsDouble() * 1000;

        logger.debug("timestamp = {}", timeStampLabel);

        var imgData = imgIn.getData();
        logger.debug("ImgData type= {}", imgData.getDataType());

//        int imgWidth = imgIn.getComponentCount();
//        int imgHeight = ((DataArray) imgIn.getElementType()).getComponentCount();

        if (imgData instanceof DataBlockByte) {

//        if (imgData instanceof DataBlockCompressed) {

//        int imgWidth = imgIn.getComponentCount();
//        int imgHeight = ((DataArray) imgIn.getElementType()).getComponentCount();
//
            int imgWidth = inputWidth.getData().getIntValue();
            int imgHeight = inputHeight.getData().getIntValue();
            byte[] imageFrame = ((DataBlockByte) imgData).getUnderlyingObject();

            logger.debug("Image frame length = {}", imageFrame.length);

//            byte[] imageFrame = ((DataBlockCompressed) imgData).getUnderlyingObject();

            // Wrap the existing byte array
            BytePointer ptr = new BytePointer(imageFrame);
            logger.debug("New Bytepointer created {}", ptr);

            // Create Mat (rows = height, cols = width)
            mat = new Mat(imgHeight, imgWidth, CV_8UC3, ptr);
            logger.debug("New mat created from frame");

            Date date = new Date((long) timeStampLabel);

            SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ");

            FeatureDetector1 detector = new FeatureDetector1(
                    cascadeClassifiers, mat, bboxList, numVehicles, imgWidth, imgHeight,
                    dateFormatter.format(date), inputTimeStamp, vehicleDetected, detectionStartTime,
                    detectionEndTime, captureTimestamp, outputHeight, outputWidth, foiId, nextId, activeVehicles, started);

            logger.debug("Feature detector created");

            imageFrame = detector.detectFeatures();

            setNextId(detector);
            setActiveVehicles(detector);
//            setStarted(detector);


            int arraySize = imageFrame.length;
            imgOut.getArraySizeComponent().getData().setIntValue(arraySize);
            imgOut.getData().setUnderlyingObject(imageFrame);


            outputWidth.getData().setIntValue(imgWidth);
            outputHeight.getData().setIntValue(imgHeight);

//            ((DataBlockByte) imgOut.getData()).setUnderlyingObject(imageFrame);
                    ((DataBlockCompressed) imgOut.getData()).setUnderlyingObject(imageFrame);

            // Copy frame timestamp
            double timestamp = inputTimeStamp.getData().getDoubleValue();
            System.out.println(timestamp );
            outputTimeStamp.getData().setDoubleValue(timestamp);

            ptr.deallocate();

            mat.release();

        } else {
            throw new IllegalArgumentException("Only DataBlockByte supported as input");
        }
    }


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

    public void setNextId(FeatureDetector1 detector){
        nextId = detector.getNextId();
    }

    public int getNextId(){
        return nextId;
    }

    public void setActiveVehicles(FeatureDetector1 detector){
        activeVehicles = detector.getActiveVehicles();
    }

    public Map<Integer, VehicleTracking> getActiveVehicles(){
        return activeVehicles;
    }


    // Add these methods similar to your other getter/setters:
//    public void setStarted(FeatureDetector1 detector) {
//    started = detector.getStarted();
//    }

    public boolean getStarted() {
        return started;
    }

    @Override
    public void dispose() {

        super.dispose();
    }
}
