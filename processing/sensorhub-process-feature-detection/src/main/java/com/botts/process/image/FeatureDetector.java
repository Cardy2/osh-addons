/***************************** BEGIN LICENSE BLOCK ***************************

 Copyright (C) 2022 Botts Innovative Research, Inc. All Rights Reserved.
 ******************************* END LICENSE BLOCK ***************************/
package com.botts.process.image;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_core.CV_8UC1;
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
class FeatureDetector {

    private final Logger logger = LoggerFactory.getLogger(FeatureDetector.class);

    // Only have a single converter per thread!
    private final Java2DFrameConverter frameConverter = new Java2DFrameConverter();

    private final byte[] imageData;

    private final String label;

    private final List<CascadeClassifier> classifiers;

    /**
     * Constructor
     *
     * @param classifiers The set of classifiers to use in performing feature detection
     * @param imageData   The raw input image data
     * @param label       The label, if any, to apply to the image
     */
    FeatureDetector(List<CascadeClassifier> classifiers, byte[] imageData, String label) {

        logger.debug("Creating");

        System.setProperty("org.bytedeco.javacpp.logger.debug", "true");
        System.setProperty("org.bytedeco.javacpp.maxphysicalbytes", "0");
        System.setProperty("org.bytedeco.javacpp.maxbytes", "0");

        this.classifiers = classifiers;

        this.imageData = java.util.Arrays.copyOf(imageData, imageData.length);

        this.label = label;

        logger.debug("Created");
    }

    /**
     * Creates an image frame with detected features and label applied.
     *
     * @return an image frame containing the post-processed image.
     */
    byte[] detectFeatures() {

        logger.debug("Detecting features");

        ByteArrayInputStream inputStream = new ByteArrayInputStream(imageData);

        BufferedImage image;

        byte[] outputImageData = null;

        try {

            image = ImageIO.read(inputStream);

            Frame imageFrame = frameConverter.convert(image);

            Mat imageMatrix = new OpenCVFrameConverter.ToMat().convert(imageFrame);

            Mat grayscaleImage = new Mat(imageFrame.imageHeight, imageFrame.imageWidth, CV_8UC1);

            cvtColor(imageMatrix, grayscaleImage, CV_BGR2GRAY);

            try (RectVector features = new RectVector()) {

                logger.debug("Applying classifiers");

                for (CascadeClassifier classifier : classifiers) {

                    classifier.detectMultiScale(grayscaleImage, features);

                    logger.debug("Detecting features");

                    for (int i = 0; i < features.size(); i++) {

                        Rect feature = features.get(i);

                        // Color given as BGR instead of RGB
                        rectangle(imageMatrix, feature, new Scalar(0, 255, 255.0, 1.0));
                    }

                    logger.debug("{} features detected", features.size());
                }

                features.deallocate();
            }

            grayscaleImage.release();

            if (null != label) {

                logger.debug("Labeling image");

                Size dimensions = getTextSize(label, FONT_HERSHEY_PLAIN, 2.0, 2, new int[]{1});

                putText(imageMatrix, label,
                        new Point(10, imageFrame.imageHeight - dimensions.height() - 1),
                        FONT_HERSHEY_PLAIN,
                        1.0,
                        new Scalar(0.0, 255.0, 255.0, 2.0));

                logger.debug("Image labeled: {}", label);
            }

            Frame outputImage = new OpenCVFrameConverter.ToIplImage().convert(imageMatrix);

            imageMatrix.release();

            image = frameConverter.convert(outputImage);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            ImageIO.write(image, "jpg", byteArrayOutputStream);

            byteArrayOutputStream.flush();

            outputImageData = byteArrayOutputStream.toByteArray();

            byteArrayOutputStream.close();

        } catch (IOException e) {

            logger.error("Exception while processing event, {}", e.toString());
        }

        logger.debug("Done detecting features");

        return outputImageData;
    }
}
