package org.sensorhub.impl.process.opencv;


import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.global.opencv_imgcodecs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import org.jcodec.codecs.h264.H264Decoder;
import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.codecs.h264.MappedH264ES;
import org.jcodec.common.NIOUtils;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Packet;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import org.jcodec.scale.RgbToYuv420p;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class ImageUtils {

    public static Mat decodeJpegToMat(byte[] jpeg) {
        Mat buf = new Mat(1, jpeg.length, opencv_core.CV_8UC1);
        buf.data().put(jpeg);
        return opencv_imgcodecs.imdecode(buf, opencv_imgcodecs.IMREAD_COLOR);
    }

    public static Mat matFromBytes(byte[] compressedImage) {
        Mat buf = new Mat(1, compressedImage.length, org.bytedeco.opencv.global.opencv_core.CV_8UC1);
        buf.data().put(compressedImage);

        // Decode the image
        Mat image = opencv_imgcodecs.imdecode(buf, opencv_imgcodecs.IMREAD_COLOR);
        buf.release(); // free temporary buffer
        return image;
    }


//    public static byte[] encodeMatToJpeg(Mat mat) {
//        Mat buf = new Mat();
//        opencv_imgcodecs.imencode(".jpg", mat, buf);
//        byte[] arr = new byte[(int) buf.total() * buf.channels()];
//        buf.data().get(arr);
//        buf.release();
//        return arr;
//    }

    public static byte[] convertH264ToBitmap(byte[] imageData, int frameWidth, int frameHeight) {

        byte[] bitmapData = null;

        try {

            // Create an H264 Elementary Stream from the imageData
            MappedH264ES es = new MappedH264ES(NIOUtils.from(ByteBuffer.wrap(imageData), 0));

            // Create a picture container
            Picture picture = Picture.create(frameWidth, frameHeight, ColorSpace.YUV420);

            H264Decoder decoder = new H264Decoder();

            // Null es means that imageData was not a valid elementary stream or had an error in it
            if (es != null) {

                // Get the packet from the frame, at this point there is only one packet per frame
                Packet packet = es.nextFrame();

                // If there is a valid packet
                if (null != packet) {

                    // Get the data contained in the packet
                    ByteBuffer data = packet.getData();

                    // decode the picture in the packet
                    Picture resultingPicture = decoder.decodeFrame(data, picture.getData());

                    // Convert to a BufferedImage
                    BufferedImage bufferedImage = AWTUtil.toBufferedImage(resultingPicture);

                    // Create a ByteArrayOutputStream to write image
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

                    // Write the image to the stream as a bitmap
                    ImageIO.write(bufferedImage, "bmp", byteArrayOutputStream);

                    // Retrieve the bitmap array
                    bitmapData = byteArrayOutputStream.toByteArray();

                    // Flush the stream
                    byteArrayOutputStream.flush();

                    // Close the stream
                    byteArrayOutputStream.close();
                }
            }

        } catch(Exception e) {
        }

        return bitmapData;
    }

    public static byte[] convertBitmapToH264(byte[] imageData, int frameWidth, int frameHeight) {

        byte[] convertedData = null;

        try {

            InputStream inputStream = new ByteArrayInputStream(imageData);

            BufferedImage bufferedImage = ImageIO.read(inputStream);

            // Create a rgbPicture container
            Picture rgbPicture = AWTUtil.fromBufferedImage(bufferedImage);

            // Create a yuvPicture container
            Picture yuvPicture = Picture.create(frameWidth, frameHeight, ColorSpace.YUV420);

            H264Encoder encoder = new H264Encoder();

            RgbToYuv420p transform = new RgbToYuv420p(0, 0);

            transform.transform(rgbPicture, yuvPicture);

            ByteBuffer buf = ByteBuffer.allocate(bufferedImage.getWidth() * bufferedImage.getHeight() * 3);

            ByteBuffer convertedBuffer = encoder.encodeFrame(yuvPicture, buf);

            convertedData = convertedBuffer.array();

        } catch(Exception e) {
        }

        return convertedData;
    }
}
