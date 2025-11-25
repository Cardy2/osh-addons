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

import au.edu.jcu.v4l4j.exceptions.UnsupportedMethod;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.videocam.VideoCamHelper;
import org.vast.data.DataBlockByte;
import au.edu.jcu.v4l4j.CaptureCallback;
import au.edu.jcu.v4l4j.DeviceInfo;
import au.edu.jcu.v4l4j.V4L4JConstants;
import au.edu.jcu.v4l4j.VideoFrame;
import au.edu.jcu.v4l4j.exceptions.V4L4JException;
import org.vast.data.DataBlockMixed;

import java.util.Arrays;
import java.util.Objects;


/**
 * <p>
 * Implementation of RGB video output for V4L sensor
 * </p>
 *
 * @author Alex Robin
 * @since Sep 5, 2013
 */
public class V4LCameraOutputRGB extends V4LCameraOutput implements CaptureCallback
{
    DataComponent camDataStruct;


    protected V4LCameraOutputRGB(V4LCameraDriver driver)
    {
        super("camOutput_RGB", driver);
    }


    @Override
    protected void init(DeviceInfo deviceInfo) throws SensorException
    {
        V4LCameraParams camParams = parentSensor.camParams;

        // init frame grabber and output
        try
        {
            initFrameGrabber(camParams);

            // adjust params to what was actually set up by V4L
            camParams.imgWidth = frameGrabber.getWidth();
            camParams.imgHeight = frameGrabber.getHeight();
            try {
                camParams.frameRate = frameGrabber.getFrameInterval().denominator / frameGrabber.getFrameInterval().numerator;
            } catch (UnsupportedMethod e) {
                getLogger().warn("Frame interval not supported; setting default FPS to 30");
                camParams.frameRate = 30;
            }
            camParams.imgFormat = frameGrabber.getImageFormat().getName();

            // create SWE output structure
            VideoCamHelper fac = new VideoCamHelper();
            dataStream = fac.newVideoOutputRGB(getName(), camParams.imgWidth, camParams.imgHeight);
        }
        catch (V4L4JException e)
        {
            throw new SensorException("Error while initializing frame grabber", e);
        }
    }

    protected void initFrameGrabber(V4LCameraParams camParams) throws V4L4JException {
        if (this.frameGrabber == null) {
            this.frameGrabber = ((V4LCameraDriver)this.parentSensor).videoDevice.getRGBFrameGrabber(camParams.imgWidth, camParams.imgHeight, 0, 7);
        }

        System.out.println(this.frameGrabber.getImageFormat());
    }

//    protected void initFrameGrabber(V4LCameraParams camParams) throws V4L4JException {
//        if (frameGrabber == null) {
//
////            if (Objects.equals(camParams.imgFormat, "RGB")) {
//                frameGrabber = parentSensor.videoDevice.getRGBFrameGrabber(camParams.imgWidth, camParams.imgHeight, 0, V4L4JConstants.IMF_RGB24);
////            } else if (Objects.equals(camParams.imgFormat, "BGR")) {
////                frameGrabber = parentSensor.videoDevice.getBGRFrameGrabber(camParams.imgWidth, camParams.imgHeight, 0, V4L4JConstants.IMF_BGR24);
////            } else {
////                frameGrabber = parentSensor.videoDevice.getRGBFrameGrabber(camParams.imgWidth, camParams.imgHeight, 0, V4L4JConstants.STANDARD_WEBCAM);
////
////            }
//            System.out.println(frameGrabber.getImageFormat());
//
//        }
//    }


//    @Override
//    protected void processFrame(VideoFrame frame) {
//
//        DataBlock dataBlock;
//        if (latestRecord == null)
//            dataBlock = dataStream.getElementType().createDataBlock();
////            dataBlock = camDataStruct.createDataBlock();
//        else
//            dataBlock = latestRecord.renew();
////
//        dataBlock.setDoubleValue(getJulianTimeStamp(frame.getCaptureTime()));
//
//        byte[] frameData = new byte[frame.getFrameLength()];
//        System.arraycopy(frame.getBytes(), 0, frameData, 0, frameData.length);
//        ((DataBlockMixed)dataBlock).getUnderlyingObject()[1].setUnderlyingObject(frameData);
//
////        dataBlock.setUnderlyingObject(frame.getBytes());
//
////        // update latest record and send event
//        latestRecord = dataBlock;
//        latestRecordTime = System.currentTimeMillis();
//        eventHandler.publish(new DataEvent(latestRecordTime, this, dataBlock));
//    }


protected void processFrame(VideoFrame frame) {

    DataBlock dataBlock = (this.latestRecord == null)
            ? this.dataStream.getElementType().createDataBlock()
            : this.latestRecord.renew();

    dataBlock.setDoubleValue(this.getJulianTimeStamp(frame.getCaptureTime()));

    Object[] fields = ((DataBlockMixed) dataBlock).getUnderlyingObject();
    DataBlockByte imgBlock = (DataBlockByte) fields[1];
    byte[] frameData = imgBlock.getUnderlyingObject();

    if (frameData == null || frameData.length != frame.getFrameLength()) {
        frameData = new byte[frame.getFrameLength()];
        imgBlock.setUnderlyingObject(frameData);
    }

    System.arraycopy(frame.getBytes(), 0, frameData, 0, frame.getFrameLength());

    this.latestRecord = dataBlock;
    this.latestRecordTime = System.currentTimeMillis();
    this.eventHandler.publish(new DataEvent(this.latestRecordTime, this, new DataBlock[]{dataBlock}));
}
}
