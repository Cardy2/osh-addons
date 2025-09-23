package org.sensorhub.impl.process.opencv;


import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.global.opencv_imgcodecs;

public class ImageUtils {
    public static Mat decodeJpegToMat(byte[] jpeg) {
        Mat buf = new Mat(1, jpeg.length, opencv_core.CV_8UC1);
        buf.data().put(jpeg);
        return opencv_imgcodecs.imdecode(buf, opencv_imgcodecs.IMREAD_COLOR);
    }

//    public static byte[] encodeMatToJpeg(Mat mat) {
//        Mat buf = new Mat();
//        opencv_imgcodecs.imencode(".jpg", mat, buf);
//        byte[] arr = new byte[(int) buf.total() * buf.channels()];
//        buf.data().get(arr);
//        buf.release();
//        return arr;
//    }
}
