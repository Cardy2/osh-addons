/***************************** BEGIN LICENSE BLOCK ***************************

 Copyright (C) 2022 Botts Innovative Research, Inc. All Rights Reserved.
 ******************************* END LICENSE BLOCK ***************************/
package com.botts.process.image;

import net.opengis.swe.v20.*;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.sensorhub.api.processing.OSHProcessInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.data.DataArrayImpl;
import org.vast.data.DataBlockCompressed;
import org.vast.process.ExecutableProcessImpl;
import org.vast.process.ProcessException;
import org.vast.swe.SWEHelper;
import org.vast.swe.helper.RasterHelper;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Process for performing feature detection on images from a video source
 *
 * @author Nick Garay
 * @since 1.0.0
 */
public class FeatureDetectionProcess extends ExecutableProcessImpl {

    public static final OSHProcessInfo INFO = new OSHProcessInfo(
            "opencv:FeatureDetection",
            "Feature Detection Algorithm",
            "Multi feature detection algorithm, can be used to detect features in video frames",
            FeatureDetectionProcess.class);

    protected static final Logger logger = LoggerFactory.getLogger(
            FeatureDetectionProcess.class);

    private final Count inputWidth;
    private final Count inputHeight;
    private final Count outputWidth;
    private final Count outputHeight;
    private final DataArray imgIn;
    private final DataArray imgOut;
    private final Time inputTimeStamp;
    private final Time outputTimeStamp;
    private final Text feature;

    private final List<CascadeClassifier> cascadeClassifiers = new ArrayList<>();

    public FeatureDetectionProcess() {

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
        
        // set encoding options so that output datablocks are generated correctly
        BinaryBlock jpegEncoding = sweFactory.newBinaryBlock();
        jpegEncoding.setCompression("JPEG");
        ((DataArrayImpl)imgOut).setEncodingInfo(jpegEncoding);
    }

    @Override
    public void init() throws ProcessException {

        logger.debug("Initializing");

        super.init();

        loadClassifiers();

        logger.debug("Initialized");
    }

    @Override
    public void execute() {

        logger.debug("Processing event");

        double timeStamp = inputTimeStamp.getValue().getAsDouble() * 1000.0;

        int imgWidth = inputWidth.getData().getIntValue();
        int imgHeight = inputHeight.getData().getIntValue();
        byte[] frameData = ((DataBlockCompressed) imgIn.getData()).getUnderlyingObject();

        byte[] imageData = FrameUtils.convertH264ToBitmap(frameData,imgWidth, imgHeight);

        if (null != imageData) {

            Date date = new Date((long) timeStamp);

            SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ");

            FeatureDetector detector = new FeatureDetector(
                    cascadeClassifiers, imageData, dateFormatter.format(date));

            byte[] imageFrame = detector.detectFeatures();

            //imageFrame = FrameUtils.convertBitmapToH264(imageFrame, imgWidth, imgHeight);

            if (imageFrame != null && imageFrame.length > 0) {

                outputWidth.getData().setIntValue(imgWidth);
                outputHeight.getData().setIntValue(imgHeight);
                ((DataBlockCompressed) imgOut.getData()).setUnderlyingObject(imageFrame);

                // Copy frame timestamp
                double timestamp = inputTimeStamp.getData().getDoubleValue();

                outputTimeStamp.getData().setDoubleValue(timestamp);
            }
        }

        logger.debug("Processed event");
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

    @Override
    public void dispose() {

        super.dispose();
    }
}
