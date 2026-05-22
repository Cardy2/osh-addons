package org.sensorhub.impl.process.opencv;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_highgui;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;
import org.opencv.core.Core;


import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.RescaleOp;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

public class LicensePlateReader {

    static ArrayList<String> detectedStrings = new ArrayList<>();

    public static void main(String[] args) throws TesseractException, IOException {

        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        // Process Video Source
        detectAndPreprocessPlate();


        // Process Isolated Image
//        File f = new File("C:/Users/cardy/Desktop/licensePlates/Texas.jpg");
//        BufferedImage ipimage = ImageIO.read(f);
//
//        preprocessPlateImg(ipimage);

        // Detect Occurences of a Specific Plate Result (usually the highest number is correct for what I call regular plates)
        // Irregular plates have stacked characters, an image segmenting characters, a skewed image, or oddly shaped characters
        HashMap<String, Integer> frequencyMap = new HashMap<>();

        for (String item : detectedStrings) {
            // Increment the frequency of the item
            frequencyMap.put(item, frequencyMap.getOrDefault(item, 0) + 1);
        }

        // Initialize variables to track the key with the largest value
        String maxKey = null;
        int maxValue = Integer.MIN_VALUE; // Start with the smallest possible integer value

        // Iterate through the HashMap to find the key with the largest value
        for (Map.Entry<String, Integer> entry : frequencyMap.entrySet()) {
            if (entry.getValue() > maxValue) {
                maxValue = entry.getValue();
                maxKey = entry.getKey();
            }
        }
        System.out.println("Key with the largest value: " + maxKey + " = " + maxValue);


    }


    public static void detectAndPreprocessPlate() throws IOException, TesseractException {

        String cascadePlatePath = "C:/Users/cardy/Desktop/us.xml";

        CascadeClassifier plateCascade = new CascadeClassifier(cascadePlatePath);

        if (plateCascade.empty()) {
            System.out.println("Error loading Plate Haar Cascade classifier.");
            return;
        }

        // Open video file using VideoCapture
        VideoCapture capture = new VideoCapture("C:/Users/cardy/Desktop/20250102_141344.mp4");
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
//            Mat thresholded = new Mat();
//            opencv_imgproc.threshold(gray, thresholded, 200, 255, opencv_imgproc.THRESH_BINARY | opencv_imgproc.THRESH_OTSU);

            Mat thresh2 = new Mat();
            opencv_imgproc.adaptiveThreshold(gray, thresh2, 255, opencv_imgproc.ADAPTIVE_THRESH_MEAN_C,
                    opencv_imgproc.THRESH_BINARY, 11, 7);
            // Apply equalizeHist to improve the image quality
//            opencv_imgproc.equalizeHist(thresholded, thresholded);
            opencv_imgproc.equalizeHist(thresh2, thresh2);

            Mat edged = new Mat();
            opencv_imgproc.Canny(thresh2, edged, 170, 200);
            imwrite("CannyEdges.jpg", edged);

            // Detect license plates using Haar Cascade
            RectVector plates = new RectVector();

            plateCascade.detectMultiScale(thresh2, plates, 1.1, 3, 0, new Size(30, 30), new Size());

            imwrite("thresholded.jpg", thresh2);

            for (int i = 0; i < plates.size(); i++) {
                // Get the detected rectangle
                Rect rect = plates.get(i);

                // Draw a rectangle around the detected license plate
                opencv_imgproc.rectangle(frame, rect.tl(), rect.br(), new Scalar(0, 255, 0, 0));

                // Optional: Denoise or enhance the image further if needed
//                opencv_imgproc.GaussianBlur(frame, thresholded, new Size(5, 5), 0);

                imwrite("thresholdFrame.jpg", frame);

                // Extract the license plate region from the image
                Mat licensePlate = new Mat(frame, rect);

                Rect plateNumbers = new Rect();
                Mat grayPlate = new Mat(licensePlate, plateNumbers);
                opencv_imgproc.cvtColor(licensePlate, grayPlate, opencv_imgproc.COLOR_BGR2GRAY);

                Mat gaussian = new Mat();
                opencv_imgproc.GaussianBlur(grayPlate, gaussian, new Size(5, 5), 0);

//                Mat edged = new Mat();
//                opencv_imgproc.Canny(gaussian, edged, 170, 200);
//                imwrite("CannyEdges.jpg", edged);

//                Mat th = new Mat();
//                opencv_imgproc.threshold(edged, th, 127, 255, opencv_imgproc.THRESH_BINARY | opencv_imgproc.THRESH_OTSU);
//
//                Mat th2 = new Mat();
//                opencv_imgproc.adaptiveThreshold(edged, th2, 255, opencv_imgproc.ADAPTIVE_THRESH_MEAN_C,
//                        opencv_imgproc.THRESH_BINARY, 11, 7);

                Mat cleaned = new Mat();
                opencv_imgproc.resize(gaussian, cleaned, new Size (rect.width()*3,rect.height()*3));

                // Remove any residual noise with an elliptical transform
                Mat kernel = opencv_imgproc.getStructuringElement(MORPH_ELLIPSE,new Size(3,3));
                Mat morph = new Mat();
                opencv_imgproc.morphologyEx(cleaned, morph, MORPH_CLOSE, kernel);


                imwrite("license_plate_image.png", morph);



                File f = new File("license_plate_image.png");
                BufferedImage ipimage = ImageIO.read(f);

                processAndReadPlate(ipimage, 1f, 0f);

            }

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


    public static void processAndReadPlate(BufferedImage ipimage, float scaleFactor, float offset) throws IOException, TesseractException {

        BufferedImage opimage = new BufferedImage(200, 100, ipimage.getType());

        // creating a 2D platform on the buffer image for drawing the new image
        Graphics2D graphic = opimage.createGraphics();

        // drawing new image starting from 0 0 of size 1050 x 1024 (zoomed images)
        // null is the ImageObserver class object
        graphic.drawImage(ipimage, 0, 0, 200, 100, null);
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
        api.setPageSegMode(13);
        // 7, 13 (more accurate?) for video;
        // 10 for individual characters;
        // 11 for close HQ plate images (characters together)

        api.setVariable("tessedit_char_whitelist", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
        api.setVariable("language_model_penalty_non_freq_dict_word", "1");
        api.setVariable("language_model_penalty_non_dict_word ", "1");
        api.setVariable("load_system_dawg", "0");
//        api.setVariable("user_patterns", "C:/Users/cardy/Desktop/us.patterns");
        api.setDatapath("C:/Program Files/Tesseract-OCR/tessdata");

        // doing OCR on the image and storing result in string str
        String str = api.doOCR(fopimage);
        System.out.println(str);

        compileResults(str);

    }


    public static void preprocessPlateImg(BufferedImage ipimage) throws IOException, TesseractException {

        String cascadePlatePath = "C:/Users/cardy/Desktop/us.xml";

        CascadeClassifier plateCascade = new CascadeClassifier(cascadePlatePath);

        Mat licensePlate = new Mat(bufferedImageToMat(ipimage));

        // Convert frame to grayscale for better detection
        Mat gray = new Mat();
        opencv_imgproc.cvtColor(licensePlate, gray, opencv_imgproc.COLOR_BGR2GRAY);

        // Threshold to binary image
        Mat thresholded = new Mat();
        opencv_imgproc.threshold(gray, thresholded, 70, 255, opencv_imgproc.THRESH_BINARY | opencv_imgproc.THRESH_OTSU);

        // Apply equalizeHist to improve the image quality
        opencv_imgproc.equalizeHist(thresholded, thresholded);

        // Detect license plates using Haar Cascade
        RectVector plates = new RectVector();

        plateCascade.detectMultiScale(thresholded, plates, 1.1, 3, 0, new Size(30, 30), new Size());

        for (int i = 0; i < plates.size(); i++) {
            // Get the detected rectangle
            Rect rect = plates.get(i);

            // Draw a rectangle around the detected license plate
            opencv_imgproc.rectangle(licensePlate, rect.tl(), rect.br(), new Scalar(0, 255, 0, 0));

            Rect plateNumbers = new Rect();
            Mat grayPlate = new Mat(licensePlate, plateNumbers);
            opencv_imgproc.cvtColor(licensePlate, grayPlate, opencv_imgproc.COLOR_BGR2GRAY);

            Mat gaussian = new Mat();
            opencv_imgproc.GaussianBlur(grayPlate, gaussian, new Size(5, 5), 0);

            Mat edged = new Mat();
            opencv_imgproc.Canny(gaussian, edged, 170, 200);
            imwrite("CannyEdges.jpg", edged);

//                HighGui.imshow("Gray->GaussianBlur", image2);
//            Mat th = new Mat();
//            opencv_imgproc.threshold(edged, th, 127, 255, opencv_imgproc.THRESH_BINARY | opencv_imgproc.THRESH_OTSU);

//                Mat th2 = new Mat();
//                opencv_imgproc.adaptiveThreshold(grayPlate, th2, 255, opencv_imgproc.ADAPTIVE_THRESH_MEAN_C,
//                        opencv_imgproc.THRESH_BINARY, 11, 7);

            Mat cleaned = new Mat();
            opencv_imgproc.resize(edged, cleaned, new Size(rect.width() * 3, rect.height() * 3));


//            // Remove any residual noise with an elliptical transform
//            Mat kernel = opencv_imgproc.getStructuringElement(MORPH_ELLIPSE, new Size(3, 3));
//            Mat morph = new Mat();
//            opencv_imgproc.morphologyEx(cleaned, morph, MORPH_CLOSE, kernel);




            imwrite("license_plate_image.png", cleaned);

            File f = new File("license_plate_image.png");
            BufferedImage plate = ImageIO.read(f);
//
            processAndReadPlate(plate, 1f, 0f);

        }
    }

    public static void compileResults(String result){

        if (!result.isEmpty()) {
            detectedStrings.add(result);
        }
    }

    private static Mat bufferedImageToMat(BufferedImage image) {
        // Convert BufferedImage to Mat (OpenCV)
        int width = image.getWidth();
        int height = image.getHeight();
        Mat mat = new Mat(height, width, CV_8UC3);
        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        mat.data().put(data);
        return mat;
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
}
