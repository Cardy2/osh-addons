package org.sensorhub.impl.process.opencv;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.global.opencv_highgui;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.RescaleOp;
import java.io.File;
import java.io.IOException;

import org.opencv.core.Core;

import javax.imageio.ImageIO;


public class LicensePlateDetector {

//        extends ExecutableProcessImpl {
//
//    public static final OSHProcessInfo INFO = new OSHProcessInfo("opencv:LicensePlateDetector", "License Plate Detector", null, LicensePlateDetector.class);
//
//    protected LicensePlateDetector(ProcessInfo processInfo) {
//        super(processInfo);
//
//    }
//

public static void processImg(BufferedImage ipimage, float scaleFactor, float offset) throws IOException, TesseractException {

    BufferedImage opimage = new BufferedImage(400, 200, ipimage.getType());

    // creating a 2D platform on the buffer image for drawing the new image
    Graphics2D graphic = opimage.createGraphics();

    // drawing new image starting from 0 0 of size 1050 x 1024 (zoomed images)
    // null is the ImageObserver class object
    graphic.drawImage(ipimage, 0, 0, 400, 200, null);
    graphic.dispose();

    // rescale OP object for gray scaling images
    RescaleOp rescale = new RescaleOp(scaleFactor, offset, null);

    // performing scaling and writing on a .png file
    BufferedImage fopimage = rescale.filter(opimage, null);
    ImageIO.write(fopimage, "jpg", new File("processed_image.png"));

    // Instantiating the Tesseract class which is used to perform OCR
    Tesseract api = new Tesseract();

        api.setLanguage("eng");         // Set OCR language
        api.setOcrEngineMode(3);
        api.setPageSegMode(11);
        api.setVariable("tessedit_char_whitelist", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
        api.setVariable("language_model_penalty_non_freq_dict_word", "1");
        api.setVariable("language_model_penalty_non_dict_word ", "1");
        api.setVariable("load_system_dawg", "0");
//        api.setVariable("user_patterns", "C:/Users/cardy/Desktop/us.patterns");
        api.setDatapath("C:/Program Files/Tesseract-OCR/tessdata/");

    // doing OCR on the image and storing result in string str
    String str = api.doOCR(fopimage);
    System.out.println(str);
}


    public static void main(String[] args) throws TesseractException, IOException{

//        System.setProperty("org.bytedeco.javacpp.logger", "slf4j");
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

            // Load the pre-trained Haar Cascade Classifier for License Plates
//            String cascadePath = "C:/Users/cardy/Desktop/haarcascade_russian_plate_number.xml";  // Adjust path
            String cascadePlatePath = "C:/Users/cardy/Desktop/us.xml";

            CascadeClassifier plateCascade = new CascadeClassifier(cascadePlatePath);

            if (plateCascade.empty()) {
                System.out.println("Error loading Plate Haar Cascade classifier.");
                return;
            }

            // Open video file using VideoCapture
            VideoCapture capture = new VideoCapture("C:/Users/cardy/Desktop/20250102_141416.mp4");
            //  "20250102_141344.mp4"
            //  "20250102_141416.mp4"
            if (!capture.isOpened()) {
                System.out.println("Error: Couldn't open video file.");
                return;
            }

            // Process the video frame by frame
            Mat frame = new Mat();
            while (capture.read(frame)) {
                if (frame.empty()) {
                    System.out.println("End of video or unable to read frame.");
                    break;
                }

                // Convert frame to grayscale for better detection
                Mat gray = new Mat();
                opencv_imgproc.cvtColor(frame, gray, opencv_imgproc.COLOR_BGR2GRAY);

                // Threshold to binary image
                Mat thresholded = new Mat();
                opencv_imgproc.threshold(gray, thresholded, 0, 255, opencv_imgproc.THRESH_BINARY| opencv_imgproc.THRESH_OTSU);

                // Apply equalizeHist to improve the image quality
                opencv_imgproc.equalizeHist(gray, gray);
//                opencv_imgproc.equalizeHist(thresholded,thresholded);

                // Detect license plates using Haar Cascade
                RectVector plates = new RectVector();

                plateCascade.detectMultiScale(thresholded, plates, 1.1, 3, 0, new Size(30,30), new Size());

                opencv_imgcodecs.imwrite("thresholded.jpg", thresholded);

                for (int i = 0; i < plates.size(); i++) {
                    Rect rect = plates.get(i);  // Get the detected rectangle
                    // Draw a rectangle around the detected license plate
//                    opencv_imgproc.rectangle(thresholded, rect.tl(), rect.br(), new Scalar(0, 255, 0, 0));
                    opencv_imgproc.rectangle(frame, rect.tl(), rect.br(), new Scalar(0, 255, 0, 0));

                    // Optional: Denoise or enhance the image further if needed
//                     opencv_imgproc.GaussianBlur(thresholded, thresholded, new org.bytedeco.opencv.opencv_core.Size(5, 5), 0);

                    // Extract the license plate region from the image
                    Mat licensePlate = new Mat(thresholded, rect);
//                    Mat licensePlate = new Mat(frame, rect);

                    opencv_imgcodecs.imwrite("license_plate_image.png", licensePlate);

                    File f = new File("license_plate_image.png");

                    BufferedImage ipimage = ImageIO.read(f);

                    processImg(ipimage,1f, 0f);

                    // getting RGB content of the whole image file
//                    double d = ipimage
//                            .getRGB(ipimage.getTileWidth() / 2,
//                                    ipimage.getTileHeight() / 2);

//                    // comparing the values and setting new scaling values that are later on used by RescaleOP
//                    if (d >= -1.4211511E7 && d < -7254228) {
//                        processImg(ipimage, 3f, -10f);
//                    }
//                    else if (d >= -7254228 && d < -2171170) {
//                        processImg(ipimage, 1.455f, -47f);
//                    }
//                    else if (d >= -2171170 && d < -1907998) {
//                        processImg(ipimage, 1.35f, -10f);
//                    }
//                    else if (d >= -1907998 && d < -257) {
//                        processImg(ipimage, 1.19f, 0.5f);
//                    }
//                    else if (d >= -257 && d < -1) {
//                        processImg(ipimage, 1f, 0.5f);
//                    }
//                    else if (d >= -1 && d < 2) {
//                        processImg(ipimage, 1f, 0.35f);
//                    }
                }


//                    // Convert the extracted region (license plate) to a BufferedImage
//                    BufferedImage bufferedImage = matToBufferedImage(licensePlate);
//
//                    Mat grayPlate = new Mat();
//                    opencv_imgproc.cvtColor(licensePlate, grayPlate, opencv_imgproc.COLOR_BGR2GRAY);

                    // Crop the detected license plate from the grayscale image
//                    Mat roiGray = new Mat(grayPlate, rect);

//                    String xmlFile = "C:/Users/cardy/Desktop/plate.xml";
//                    CascadeClassifier classifier = new CascadeClassifier(xmlFile);
//
//                    // Detect eyes in the face region
//                    RectVector plateNumbers = new RectVector();
//                    plateCascade.detectMultiScale(grayPlate, plateNumbers, 1.1, 3, 0, new Size(30,30), new Size());
//
//                    // Iterate through the detected plate numbers
//                    for (int ii = 0; ii < plateNumbers.size(); ii++) {
//                        Rect numberRect = plateNumbers.get(ii);  // Get the detected rectangle around plate numbers
//                        // Draw rectangles around the numbers
//                        opencv_imgproc.rectangle(grayPlate, numberRect.tl(), numberRect.br(), new Scalar(0, 255, 255, 0));
//
//                        Mat numbers = new Mat(licensePlate, numberRect);
//                        BufferedImage bufferedNumbers = matToBufferedImage(numbers);

//
//                        String individualPlateNumbers = api.doOCR(bufferedNumbers);
//                       if (individualPlateNumbers != null && !individualPlateNumbers.isEmpty()) {
//                        System.out.println("Detected License Plate Text: " + individualPlateNumbers);
//                    } else {
//                        System.out.println("OCR failed to detect text.");
//                    }
//                    }

                    // Perform OCR to extract the text
//                    String result = api.doOCR(bufferedImage);
//
//                    System.out.println(result);
//                    Mat processedImage = preprocessImage(bufferedImage);
//                    opencv_imgcodecs.imwrite("processed_image.png", processedImage);
//
//                    if (result != null && !result.isEmpty()) {
//                        System.out.println("Detected License Plate Text: " + licensePlateNumber);
//                    } else {
//                        System.out.println("OCR failed to detect text.");
//                    }

//                    // Perform OCR to read text from the license plate
//                    String licensePlateText = api.doOCR(bufferedImage);
//
//                    // Print the detected text (license plate number)
//                    System.out.println("Detected License Plate: " + licensePlateText);
//                }


                // Show the frame with detected license plates
                opencv_highgui.imshow("License Plate Detection", frame);

                // Exit if Escape key is pressed
                if (opencv_highgui.waitKey(1) == 27) { // Escape key
                    break;
                }
            }

            // Release the video capture and destroy all windows
            capture.release();
            opencv_highgui.destroyAllWindows();
        }


    public static BufferedImage matToBufferedImage(Mat mat) {
        // Ensure the mat is in the correct format (8-bit, 3 channels, BGR)
        if (mat.channels() == 1) {
            // If it's grayscale, convert to 3 channels (BGR)
            Mat matBGR = new Mat();
            opencv_imgproc.cvtColor(mat, matBGR, opencv_imgproc.COLOR_BGR2GRAY);
            mat = matBGR;
        }

        // Create a BufferedImage with the same dimensions as the mat
        int width = mat.cols();
        int height = mat.rows();
        int channels = mat.channels();
        int type = BufferedImage.TYPE_3BYTE_BGR;  // BGR format for OpenCV

        // Create a new BufferedImage
        BufferedImage image = new BufferedImage(width, height, type);

        // Get the data from the mat and set it into the BufferedImage
        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        mat.data().get(data);

        return image;
    }

    private static Mat preprocessImage(BufferedImage image) {
        // Convert the BufferedImage to OpenCV Mat
        Mat mat = bufferedImageToMat(image);

        // Convert to grayscale
        Mat gray = new Mat();
        opencv_imgproc.cvtColor(mat, gray, opencv_imgproc.COLOR_BGR2GRAY);

        // Apply threshold to make it binary (black and white)
        Mat binary = new Mat();
        opencv_imgproc.threshold(gray, binary, 0, 255, opencv_imgproc.THRESH_BINARY | opencv_imgproc.THRESH_OTSU);

        return gray;
    }

    private static Mat bufferedImageToMat(BufferedImage image) {
        // Convert BufferedImage to Mat (OpenCV)
        int width = image.getWidth();
        int height = image.getHeight();
        Mat mat = new Mat(height, width, opencv_core.CV_8UC3);
        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        mat.data().put(data);
        return mat;
    }
}
