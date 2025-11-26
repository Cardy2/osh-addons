/***************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are subject to the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one
 at http://mozilla.org/MPL/2.0/.

 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 for the specific language governing rights and limitations under the License.

 Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.

 ******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.v4l;

import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import au.edu.jcu.v4l4j.DeviceInfo;
import au.edu.jcu.v4l4j.ImageFormat;
import au.edu.jcu.v4l4j.VideoDevice;
//import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.sensorML.SMLUtils;
import org.vast.xml.XMLWriterException;

import java.io.IOException;
import java.util.UUID;


/**
 * <p>
 * Generic driver implementation for most camera compatible with Video4Linux.
 * This implementation makes use of the V4L4J library to connect to the V4L
 * native layer via libv4l4j and libvideo.
 * </p>
 *
 * @author Alex Robin
 * @since Sep 5, 2013
 */
public class V4LCameraDriver extends AbstractSensorModule<V4LCameraConfig>
{
    VirtualCam virtualCam;
    V4LCameraParams camParams;
    VideoDevice videoDevice;
    V4LCameraOutput dataInterface;
    V4LCameraControl controlInterface;


    static
    {
        try
        {
            // preload libvideo so it is extracted from JAR
            System.loadLibrary("video");
            System.loadLibrary("v4l4j");   // extracts + loads libv4l4j.so

        }
        catch (Exception e)
        {
            LoggerFactory.getLogger(V4LCameraDriver.class).error("Unable to load native v4l library", e);
        }
    }

//    static {
//        try {
//            NativeLibrary instance = NativeLibrary.getInstance("video", ClassLoader.getSystemClassLoader());
////
//////        Native.register(org.openkinect.freenect.Freenect.class, instance);
//            Native.register(au.edu.jcu.v4l4j.examples.videoViewer.DeviceChooser.class, instance);
//        } catch (Exception e)
//        {
//            LoggerFactory.getLogger(V4LCameraDriver.class).error("Unable to load native v4l library", e);
//        }
//        }


    public V4LCameraDriver()
    {

    }

    //    @Override
//    protected void beforeInit() {
//        if (config.virtualCamEnabled) {
//            try {
//                virtualCam = new VirtualCam(config.deviceName, config.virtualCam);
//                virtualCam.start();
//            } catch (IOException | InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//        }
//    }
    @Override
    protected void doInit() throws SensorHubException
    {
        super.doInit();

        this.camParams = config.defaultParams.clone();

        if (config.virtualCamEnabled) {
            try {
                virtualCam = new VirtualCam(config.deviceName, config.virtualCam, config.vcodec, config.pix_format, config.pix_format_convert, this.camParams, config.videoNr);
                virtualCam.start();

            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // generate IDs
        generateUniqueID("urn:osh:sensor:v4l-cam:", config.serialNumber);
        generateXmlID("V4L_CAMERA_", config.serialNumber);
//        this.camParams = config.defaultParams.clone();

        // init video device
        DeviceInfo deviceInfo = initVideoDevice();
        var nativeFormats = deviceInfo.getFormatList().getNativeFormats();

        if (nativeFormats == null || nativeFormats.isEmpty())
            throw new SensorException("Video device " + config.deviceName + " cannot be used for capture");

        // init video output
        for (ImageFormat fmt: nativeFormats)
        {
            if ("MJPEG".equals(fmt.getName()))
            {
                logger.debug("Creating MJPEG output");
                dataInterface = new V4LCameraOutputMJPEG(this, fmt);
            }
            else if ("H264".equals(fmt.getName()))
            {
                getLogger().debug("Creating H264 output");
                dataInterface = new V4LCameraOutputH264(this, fmt);
            }
        }

        if (dataInterface == null)
        {
            getLogger().debug("Creating RGB output");
            dataInterface = new V4LCameraOutputRGB(this);
        }

        dataInterface.init(deviceInfo);
        addOutput(dataInterface, false);

        // init control interface
        this.controlInterface = new V4LCameraControl(this);
        controlInterface.init(deviceInfo);
        addControlInput(controlInterface);
    }


    protected DeviceInfo initVideoDevice() throws SensorException {
        if (!config.virtualCamEnabled) {
            try {
                videoDevice = new VideoDevice(config.deviceName);
                return videoDevice.getDeviceInfo();
            } catch (Throwable e) {
                throw new SensorException("Cannot initialize video device " + config.deviceName, e);
            }
        } else {
            try {
                videoDevice = new VideoDevice(config.virtualCam);
                return videoDevice.getDeviceInfo();
            } catch (Throwable e) {
                throw new SensorException("Cannot initialize video device " + config.virtualCam, e);
            }
        }
    }


    @Override
    protected void doStart() throws SensorException
    {
        if (videoDevice == null)
            initVideoDevice();

        // start video streaming
        if (dataInterface != null)
            dataInterface.start();

//        SMLUtils smlUtils = new SMLUtils(SMLUtils.V2_1);
//
//        try {
//            smlUtils.writeProcess(System.out, this.getCurrentDescription(), true);
//        } catch (XMLWriterException e) {
//            throw new RuntimeException(e);
//        }
    }

//    public void writeAsXML(V4LCameraDriver this, V4LCameraConfig config) throws XMLWriterException, SensorHubException {
//        SMLUtils smlUtils = new SMLUtils(SMLUtils.V2_1);
//        V4LCameraDriver v4LCameraDriver = new V4LCameraDriver();
//        V4LCameraConfig cameraConfig = new V4LCameraConfig();
//        config.id = UUID.randomUUID().toString();
//        this.init(config);
//        smlUtils.writeProcess(System.out, this.getCurrentDescription(), true);
//    }

    @Override
    protected void doStop()
    {
        if (dataInterface != null)
            dataInterface.stop();

        if (controlInterface != null)
            controlInterface.stop();

        if (videoDevice != null)
        {
            videoDevice.release();
            videoDevice = null;
        }
        if (virtualCam != null) {
            try {
                if (virtualCam.isRunning()) {
                    virtualCam.stop();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }


    public void updateParams(V4LCameraParams params) throws SensorException
    {
        // cleanup framegrabber and restart video output
        dataInterface.stop();
        dataInterface.start();
    }


    @Override
    protected void updateSensorDescription()
    {
        synchronized (sensorDescLock)
        {
            super.updateSensorDescription();

            if (!sensorDescription.isSetDescription())
                sensorDescription.setDescription("Video4Linux camera on port " + videoDevice.getDevicefile());
        }
    }


    @Override
    public boolean isConnected()
    {
        return (videoDevice != null);
    }


    @Override
    public void cleanup()
    {

    }
}
