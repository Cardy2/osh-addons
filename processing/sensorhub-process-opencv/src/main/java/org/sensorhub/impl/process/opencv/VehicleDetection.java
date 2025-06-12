package org.sensorhub.impl.process.opencv;

import net.opengis.swe.v20.*;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_core.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.sensorhub.api.processing.OSHProcessInfo;
import org.vast.data.DataArrayImpl;
import org.vast.data.DataBlockByte;
import org.vast.process.ExecutableProcessImpl;
import org.vast.process.ProcessException;
import org.vast.swe.SWEHelper;

public class VehicleDetection extends ExecutableProcessImpl {
    public static final OSHProcessInfo INFO = new OSHProcessInfo("opencv:VehicleDetection", "Vehicle Detection Algorithm", null, VehicleDetection.class);

    Count inputWidth;
    Count inputHeight;
    DataArray imgIn;
    Time outputTimeStamp;
    Count numVehicles;
    DataArray bboxList;
    Category modeParam;
    Text configFileParam;

    enum ModeEnum {CONTINUOUS} //ONE_SHOT

    ModeEnum mode;
    CascadeClassifier vehicle_cascade;
    RectVector detectedObjects = new RectVector();

    public VehicleDetection() {
        super(INFO);
        var swe = new CVHelper();

        // inputs
        inputData.add("rgbFrame", swe.createRecord()
                .label("Video Frame")
                .addField("time", swe.createTime()
                        .asSamplingTimeIsoUTC()
                        .label("Frame Timestamp")
                        .build())
                .addField("width", inputWidth = swe.createCount()
                        .id("IN_WIDTH")
                        .label("Input Frame Width")
                        .build())
                .addField("height", inputHeight = swe.createCount()
                        .id("IN_HEIGHT")
                        .label("Input Frame Height")
                        .build())
                .addField("img", imgIn = swe.newRgbImage(
                        inputWidth,
                        inputHeight,
                        DataType.BYTE))
                .build());

        // parameters

        paramData.add("detectionMode", modeParam = swe.createCategory()
                .definition(SWEHelper.getPropertyUri("ModeID"))
                .addAllowedValues(ModeEnum.class)
                .build());

        paramData.add("configFile", configFileParam = swe.createText()
                .definition(SWEHelper.getPropertyUri("Path"))
                .label("Classifier Config File")
                .description("Path of the XML file containing the Haar cascade configuration (OpenCV format")
                .build());

        // outputs

        outputData.add("detectedVehicles", swe.createRecord()
                .label("Detected Vehicles")
//                .addField("time", outputTimeStamp = swe.createTime()
//                        .asSamplingTimeIsoUTC()
//                        .label("Frame Timestamp")
//                        .build())
                .addField("numVehicles", numVehicles = swe.createCount()
                        .id("NUM_VEHICLES")
                        .build())
                .addField("bboxList", bboxList = swe.createBboxList(numVehicles)
                        .build())
                .build());
    }

    @Override
    public void init() throws ProcessException {
        super.init();

        try {
            var val = modeParam.getData().getStringValue();
            if (val != null)
                mode = ModeEnum.valueOf(val);
            else
                mode = ModeEnum.CONTINUOUS; //default
        } catch (IllegalArgumentException e) {
            reportError("Unsupported mode. Must be one of " + Arrays.toString(ModeEnum.values()));
        }

        // read config file

        var configFile = configFileParam.getData().getStringValue();
        if (configFile == null || !Files.isReadable(Path.of(configFile)))
            reportError("Missing or inaccessible config file: " + configFile);

        this.vehicle_cascade = new CascadeClassifier(configFile);
    }

    @Override
    public void execute() throws ProcessException {

        var rows = imgIn.getComponentCount();
        var cols = ((DataArray) imgIn.getElementType()).getComponentCount();
        var imgData = imgIn.getData();

        // convert input image data to OpenCV Mat object

        Mat mat;
        if (imgData instanceof DataBlockByte) {
            var imgBytes = ((DataBlockByte) imgData).getUnderlyingObject();

            mat = new Mat(rows, cols, CV_8UC(3), new BytePointer(imgBytes)); // 8-bit unsigned integer matrix/image with 3 channels
        } else
            throw new IllegalArgumentException("Only DataBlockByte supported as input");

        detectedObjects.clear();
        vehicle_cascade.detectMultiScale(mat, detectedObjects);

        long numberOfVehicles = detectedObjects.size();
        numVehicles.getData().setIntValue((int) numberOfVehicles);
        bboxList.updateSize();
        var bboxData = bboxList.getData();

        int idx = 0;
        for (int i = 0; i < numberOfVehicles; i++) {
            Rect rect = detectedObjects.get(i);
            System.out.format("Vehicle detected @ %d, %d, size = %dx%d/n", rect.x(), rect.y(), rect.width(), rect.height());
            bboxData.setIntValue(idx++, rect.x());
            bboxData.setIntValue(idx++, rect.y());
            bboxData.setIntValue(idx++, rect.width());
            bboxData.setIntValue(idx++, rect.height());
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        if (detectedObjects != null) {
            detectedObjects.deallocate();
        }
    }
}


