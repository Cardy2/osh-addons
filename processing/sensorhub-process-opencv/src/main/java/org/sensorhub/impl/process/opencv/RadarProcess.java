package org.sensorhub.impl.process.opencv;

import net.opengis.swe.v20.*;
import net.opengis.swe.v20.Boolean;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.sensorhub.api.processing.OSHProcessInfo;
import org.sensorhub.api.sensor.ISensorModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.data.AbstractDataComponentImpl;
import org.vast.data.DataArrayImpl;
import org.vast.data.DataBlockByte;
import org.vast.data.DataBlockCompressed;
import org.vast.process.ExecutableProcessImpl;
import org.vast.process.ProcessException;
import org.vast.swe.helper.RasterHelper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.opencv.core.CvType.CV_8UC3;

public class RadarProcess extends ExecutableProcessImpl {

    public static final OSHProcessInfo INFO = new OSHProcessInfo(
            "opencv:Radar",
            "Radar Detection Process",
            "Process to determine if speed exceeds a certain threshold",
            RadarProcess.class);

    protected static final Logger logger = LoggerFactory.getLogger(
            RadarProcess.class);

    private final Time startTime;
    private final Time endTime;

    private final Count inputVelocity;
    private final Count outputVelocity;

    private final Boolean vehicleDetected;
    private final Boolean overThreshold;
    private final Count paramThreshold;
    private final Count overThresholdParam;


    private final Time captureTimestampIn;
    private final Time captureTimestampOut;
    private final Count captureWidth;
    private final Count captureHeight;
    private final DataArray captureImageOut;

    private final Time inputTimeStamp;
    private final DataArray imgIn;
    private final Count inputWidth;
    private final Count inputHeight;

    double threshold;


    public RadarProcess() {

        super(INFO);

        RasterHelper sweFactory = new RasterHelper();


        inputData.add("radarInputs", sweFactory.createRecord()
                .label("Radar")
                .addField("startTime", startTime = sweFactory.createTime()
                        .asSamplingTimeIsoUTC()
                        .label("Detection Start Time")
                        .description("Time of vehicle detection")
                        .build())
                .addField("endTime", endTime = sweFactory.createTime()
                        .asSamplingTimeIsoUTC()
                        .label("Detection End Time")
                        .description("End time of vehicle detection")
                        .build())
                .addField("vehicleDetected", vehicleDetected = sweFactory.createBoolean()
                        .id("VEHICLE_DETECTED")
                        .label("Vehicle Detected")
                        .description("Boolean value for vehicle in frame")
                        .build())
                .addField("velocity", inputVelocity = sweFactory.createCount()
                        .id("VELOCITY")
                        .label("Velocity")
                        .description("Speed of vehicle in time frame")
                        .build())
                .addField("captureTimestamp", captureTimestampIn = sweFactory.createTime()
                        .asSamplingTimeIsoUTC()
                        .id("VEHICLE_TIME")
                        .label("Time vehicle is captured in img")
                        .build())
                .build());


        inputData.add("rgbFrame", sweFactory.createRecord()
                .label("Video Frame")
                .addField("time", inputTimeStamp = sweFactory.createTime()
                        .asSamplingTimeIsoUTC()
                        .build())
                .addField("width", inputWidth = sweFactory.createCount()
                        .id("OUT_WIDTH")
                        .label("input Frame Width")
                        .build())
                .addField("height", inputHeight = sweFactory.createCount()
                        .id("OUT_HEIGHT")
                        .label("input Frame Height")
                        .build())
                .addField("img", imgIn = sweFactory.newRgbImage(
                        inputWidth,
                        inputHeight,
                        DataType.BYTE))
                .build());

        BinaryBlock mjpegEncoding = sweFactory.newBinaryBlock();
        mjpegEncoding.setCompression("MJPEG");
        ((DataArrayImpl) imgIn).setEncodingInfo(mjpegEncoding);


        paramData.add("thresholdParam", paramThreshold = sweFactory.createCount()
                .label("Vehicle Threshold Speed")
                .build());

        outputData.add("radarOutputs", sweFactory.createRecord()
                .label("Radar Data")
                .addField("velocity", outputVelocity = sweFactory.createCount()
                        .id("VELOCITY")
                        .label("Velocity")
                        .description("Speed of vehicle in time frame")
                        .build())
                .addField("speedThresholdBoolean", overThreshold = sweFactory.createBoolean()
                        .id("OVER_THRESHOLD")
                        .label("Boolean determining whether vehicle has exceeded the set threshold")
                        .build())
                .addField("overThresholdParam", overThresholdParam = sweFactory.createCount()
                        .label("Vehicle Threshold Speed")
                        .build())
                .build());

        outputData.add("vehicleCapture", sweFactory.createRecord()
                .label("Vehicle Capture Image")
                .addField("captureTime", captureTimestampOut = sweFactory.createTime()
                        .asSamplingTimeIsoUTC()
                        .label("Capture Time")
                        .build())
                .addField("width", captureWidth = sweFactory.createCount()
                        .id("IMG_WIDTH")
                        .label("input Frame Width")
                        .build())
                .addField("height", captureHeight = sweFactory.createCount()
                        .id("IMG_HEIGHT")
                        .label("input Frame Height")
                        .build())
                .addField("captureImage", captureImageOut = sweFactory.newRgbImage(captureWidth, captureHeight, DataType.BYTE))
                .build());

        BinaryComponent sampleTimeEnc = sweFactory.newBinaryComponent();
        sampleTimeEnc.setRef("/time");
        sampleTimeEnc.setCdmDataType(DataType.DOUBLE);
        ((AbstractDataComponentImpl)captureTimestampOut).setEncodingInfo(sampleTimeEnc);

        BinaryBlock mjpegEncodingOut = sweFactory.newBinaryBlock();
        mjpegEncodingOut.setCompression("MJPEG");
        mjpegEncodingOut.setRef("/img");
        ((DataArrayImpl) captureImageOut).setEncodingInfo(mjpegEncodingOut);

    }

    @Override
    public void init() throws ProcessException {

        logger.debug("Initializing");

        super.init();

        logger.debug("Initialized");
    }

    @Override
    public void execute() {

        threshold = paramThreshold.getData().getDoubleValue();
        overThresholdParam.getData().setDoubleValue(threshold);

        boolean detected = vehicleDetected.getData().getBooleanValue();

        double timeStamp = inputTimeStamp.getData().getDoubleValue();

        var imgData = imgIn.getData();

        if (imgData instanceof DataBlockCompressed) {
//        if (imgData instanceof DataBlockByte) {

            double detectionStartTime = startTime.getData().getDoubleValue();
            double detectionEndTime = endTime.getData().getDoubleValue();
            double captureTime = captureTimestampIn.getData().getDoubleValue();

//            byte[] imageFrame = ((DataBlockCompressed) imgData).getUnderlyingObject();
            if (!detected){
                overThreshold.getData().setBooleanValue(false);
            } else if (detectionStartTime < detectionEndTime) {

                double velocity = inputVelocity.getValue();
                velocity = Math.abs(velocity);
                outputVelocity.getData().setDoubleValue(velocity);

                if (velocity < threshold) {
                    overThreshold.getData().setBooleanValue(false);
                } else if (velocity > threshold) {
                    overThreshold.getData().setBooleanValue(true);

                    byte[] imageFrame = ((DataBlockCompressed) imgData).getUnderlyingObject();
//                    byte[] imageFrame = ((DataBlockByte) imgData).getUnderlyingObject();

                    try {
                        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageFrame));

                        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                            ImageIO.write(image, "jpg", baos);
                            baos.flush();
                            byte[] jpeg = baos.toByteArray();

//                            int arraySize = jpeg.length;
                            int arraySize = imageFrame.length;

                            captureWidth.getData().setIntValue(image.getWidth());
                            captureHeight.getData().setIntValue(image.getHeight());
                            captureImageOut.getArraySizeComponent().getData().setIntValue(arraySize);
//                            captureImageOut.getData().setUnderlyingObject(jpeg);
                            captureImageOut.getData().setUnderlyingObject(imageFrame);
                            captureImageOut.getEncoding();
                            captureTimestampOut.getData().setDoubleValue(captureTime);
                        }


                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    @Override
    public void dispose() {

        super.dispose();
    }
}
