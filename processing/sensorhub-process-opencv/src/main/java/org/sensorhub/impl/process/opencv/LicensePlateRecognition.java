package org.sensorhub.impl.process.opencv;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;
import org.opencv.highgui.HighGui;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.core.CvType;

import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.opencv.imgcodecs.Imgcodecs.imwrite;
import static org.opencv.imgproc.Imgproc.*;

public class LicensePlateRecognition {

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

        // Path to the Haar Cascade classifier for license plates
        String cascadePlatePath = "C:/Users/cardy/Desktop/us.xml";

        CascadeClassifier plateCascade = new CascadeClassifier(cascadePlatePath);

        if (plateCascade.empty()) {
            System.out.println("Error loading Plate Haar Cascade classifier.");
            return;
        }

        // Open video file using VideoCapture
        VideoCapture capture = new VideoCapture("C:/Users/cardy/Desktop/20250102_141416.mp4");

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
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);

            // Threshold to binary image
            Mat thresholded = new Mat();
//            Imgproc.threshold(gray, thresholded, 70, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
            Imgproc.adaptiveThreshold(gray, thresholded, 255, ADAPTIVE_THRESH_MEAN_C,
                    Imgproc.THRESH_BINARY, 11, 7);

            // Apply equalizeHist to improve the image quality
            Imgproc.equalizeHist(thresholded, thresholded);

            // Detect license plates using Haar Cascade
            MatOfRect plates = new MatOfRect();
            plateCascade.detectMultiScale(thresholded, plates, 1.1, 3, 0, new Size(30, 30), new Size());

            // Save the thresholded image
            Imgcodecs.imwrite("thresholded.jpg", thresholded);

            // Process each detected plate
            for (Rect rect : plates.toArray()) {
                // Draw a rectangle around the detected license plate
                Imgproc.rectangle(thresholded, rect.tl(), rect.br(), new Scalar(0, 255, 0), 2);

                // Save the frame with detected plates
                Imgcodecs.imwrite("thresholdFrame.jpg", thresholded);

                // Extract the license plate region from the image
                Mat licensePlate = new Mat(frame, rect);

                Rect plateNumbers = new Rect();
                Mat grayPlate = new Mat(licensePlate, plateNumbers);
                Imgproc.cvtColor(licensePlate, grayPlate, Imgproc.COLOR_BGR2GRAY);
//
//                Mat th = new Mat();
//                Imgproc.threshold(grayPlate, th, 127, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);

                Mat th2 = new Mat();
                Imgproc.adaptiveThreshold(grayPlate, th2, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C,
                        Imgproc.THRESH_BINARY, 11, 7);

                Imgproc.equalizeHist(th2, th2);

//                Mat cleaned = new Mat();
//                Imgproc.resize(th, cleaned, new Size(rect.width*3,rect.height*3));

                // Remove any residual noise with an elliptical transform
//                Mat kernel = Imgproc.getStructuringElement(MORPH_ELLIPSE,new Size(3,3));
//                Mat morph = new Mat();
//                Imgproc.morphologyEx(cleaned, morph, MORPH_CLOSE, kernel);

                // Perform edge detection
//                Mat edged = new Mat();
//                Canny(grayPlate, edged, 170, 200);
//                Imgcodecs.imwrite("CannyEdges.jpg", edged);
                
                Mat image2 = th2.clone();
                Mat drawing;
//
//                Imgproc.cvtColor(image2, image2, Imgproc.COLOR_BGR2GRAY);
//                HighGui.imshow("Original->Gray", image2);
//
//                Imgproc.adaptiveThreshold(image2, image2, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C,
//                        Imgproc.THRESH_BINARY, 11, 7);
//                HighGui.imshow("Original->Threshold", image2);
//
//                Imgproc.resize(image2, image2, new Size(rect.width*3,rect.height*3));
//                HighGui.imshow("Original->Resize", image2);
//
//                Canny(image2, image2, 170, 200);
//                HighGui.imshow("Original->Canny", image2);
////
//                Imgcodecs.imwrite("CannyEdges.jpg", image2);


//                Imgproc.GaussianBlur(image2, image2, new Size(5, 5), 0);
//                HighGui.imshow("Gray->GaussianBlur", image2);
//                Imgproc.threshold(image2, image2, 127, 255, Imgproc.THRESH_OTSU);
//                HighGui.imshow("GaussianBlur->threshold", image2);
//
//
//                // Finding contours
                List<MatOfPoint> contours = new ArrayList<>();
                Mat hierarchy = new Mat();
                Imgproc.findContours(image2, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

                List<MatOfPoint> contoursPoly = new ArrayList<>(contours.size());
                List<Rect> boundRect = new ArrayList<>(contours.size());
                List<Rect> boundRect2 = new ArrayList<>();

                // Bind rectangle to every contour
                for (int i = 0; i < contours.size(); i++) {
                    MatOfPoint2f contour2f = new MatOfPoint2f(contours.get(i).toArray());
                    MatOfPoint2f approx = new MatOfPoint2f();
                    Imgproc.approxPolyDP(contour2f, approx, 1, true);
                    contoursPoly.add(new MatOfPoint(approx.toArray()));
                    boundRect.add(Imgproc.boundingRect(new MatOfPoint(approx.toArray())));
                }

                drawing = Mat.zeros(image2.size(), CvType.CV_8UC3);

                int refineryCount = 0;
                for (int i = 0; i < contours.size(); i++) {
                    double ratio = (double) boundRect.get(i).height / boundRect.get(i).width;

                    // Filtering rectangles height/width ratio, and size.
                    if (ratio <= 2.5 && ratio >= 0.5 && boundRect.get(i).area() <= 700 && boundRect.get(i).area() >= 100) {
                        Imgproc.drawContours(drawing, contours, i, new Scalar(0, 255, 255), 1, 8, hierarchy, 0, new Point());
                        Imgproc.rectangle(drawing, boundRect.get(i).tl(), boundRect.get(i).br(), new Scalar(255, 0, 0), 1, 8, 0);

                        // Include only suitable rectangles
                        boundRect2.add(boundRect.get(i));
                        refineryCount++;
                    }
                }

                // Resize refinery rectangle array
                // Note: This step is handled by boundRect2 directly in Java as a dynamic list

                HighGui.imshow("Contours&Rectangles", drawing);
                Imgcodecs.imwrite("contours.jpg", drawing);

                HighGui.waitKey();
//                 Save the cleaned image
                Imgcodecs.imwrite("cleanedPlate.jpg", th2);
            }

//            imwrite("license_plate_image.png", grayPlate);


//            File f = new File("license_plate_image.png");
            File f = new File("cleanedPlate.jpg");
            BufferedImage ipimage = ImageIO.read(f);

            processAndReadPlate(ipimage, 1f, 0f);


            HighGui.imshow("License Plate Detection", frame);

            // Exit if Escape key is pressed
            if (HighGui.waitKey(1) == 27) { // Escape key
                break;
            }

            // Release the video capture and destroy all windows

            capture.release();
            HighGui.destroyAllWindows();
        }
    }

    public static void contourProcessing (Mat image){

            // Read the input image
            Mat image2 = image.clone();
            Mat drawing;

            // Image processing for contours
            Imgproc.cvtColor(image2, image2, Imgproc.COLOR_BGR2GRAY);
            HighGui.imshow("Original->Gray", image2);
            Imgproc.GaussianBlur(image2, image2, new Size(5, 5), 0);
            HighGui.imshow("Gray->GaussianBlur", image2);
            Canny(image2, image2, 100, 300, 3);
            HighGui.imshow("GaussianBlur->Canny", image2);

            // Finding contours
            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            Imgproc.findContours(image2, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

            List<MatOfPoint> contoursPoly = new ArrayList<>(contours.size());
            List<Rect> boundRect = new ArrayList<>(contours.size());
            List<Rect> boundRect2 = new ArrayList<>();

            // Bind rectangle to every contour
            for (int i = 0; i < contours.size(); i++) {
                MatOfPoint2f contour2f = new MatOfPoint2f(contours.get(i).toArray());
                MatOfPoint2f approx = new MatOfPoint2f();
                Imgproc.approxPolyDP(contour2f, approx, 1, true);
                contoursPoly.add(new MatOfPoint(approx.toArray()));
                boundRect.add(Imgproc.boundingRect(new MatOfPoint(approx.toArray())));
            }

            drawing = Mat.zeros(image2.size(), CvType.CV_8UC3);

            int refineryCount = 0;
            for (int i = 0; i < contours.size(); i++) {
                double ratio = (double) boundRect.get(i).height / boundRect.get(i).width;

                // Filtering rectangles height/width ratio, and size.
                if (ratio <= 2.5 && ratio >= 0.5 && boundRect.get(i).area() <= 700 && boundRect.get(i).area() >= 100) {
                    Imgproc.drawContours(drawing, contours, i, new Scalar(0, 255, 255), 1, 8, hierarchy, 0, new Point());
                    Imgproc.rectangle(drawing, boundRect.get(i).tl(), boundRect.get(i).br(), new Scalar(255, 0, 0), 1, 8, 0);

                    // Include only suitable rectangles
                    boundRect2.add(boundRect.get(i));
                    refineryCount++;
                }
            }

            // Resize refinery rectangle array
            // Note: This step is handled by boundRect2 directly in Java as a dynamic list

            HighGui.imshow("Contours&Rectangles", drawing);
            HighGui.waitKey();
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

//
//    public static void preprocessPlateImg(BufferedImage ipimage) throws IOException, TesseractException {
//
//        String cascadePlatePath = "C:/Users/cardy/Desktop/us.xml";
//
//        CascadeClassifier plateCascade = new CascadeClassifier(cascadePlatePath);
//
//        Mat licensePlate = new Mat(bufferedImageToMat(ipimage));
//
//        // Convert frame to grayscale for better detection
//        Mat gray = new Mat();
//        Imgproc.cvtColor(licensePlate, gray, Imgproc.COLOR_BGR2GRAY);
//
//        // Threshold to binary image
//        Mat thresholded = new Mat();
//        Imgproc.threshold(gray, thresholded, 70, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
//
//        // Apply equalizeHist to improve the image quality
//        Imgproc.equalizeHist(thresholded, thresholded);
//
//        // Detect license plates using Haar Cascade
//        RectVector plates = new RectVector();
//
//        plateCascade.detectMultiScale(thresholded, plates, 1.1, 3, 0, new Size(30, 30), new Size());
//
//        for (int i = 0; i < plates.size(); i++) {
//            // Get the detected rectangle
//            Rect rect = plates.get(i);
//
//            // Draw a rectangle around the detected license plate
//            Imgproc.rectangle(licensePlate, rect.tl(), rect.br(), new Scalar(0, 255, 0, 0));
//
//            Rect plateNumbers = new Rect();
//            Mat grayPlate = new Mat(licensePlate, plateNumbers);
//            Imgproc.cvtColor(licensePlate, grayPlate, Imgproc.COLOR_BGR2GRAY);
//
//            Mat th = new Mat();
//            Imgproc.threshold(grayPlate, th, 127, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
//
////                Mat th2 = new Mat();
////                Imgproc.adaptiveThreshold(grayPlate, th2, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C,
////                        Imgproc.THRESH_BINARY, 11, 7);
//
//            Mat cleaned = new Mat();
//            Imgproc.resize(th, cleaned, new Size(rect.width() * 3, rect.height() * 3));
//
//            // Remove any residual noise with an elliptical transform
//            Mat kernel = Imgproc.getStructuringElement(MORPH_ELLIPSE, new Size(3, 3));
//            Mat morph = new Mat();
//            Imgproc.morphologyEx(cleaned, morph, MORPH_CLOSE, kernel);
//
//            imwrite("license_plate_image.png", morph);
//
//            File f = new File("license_plate_image.png");
//            BufferedImage plate = ImageIO.read(f);
////
//            processAndReadPlate(plate, 1f, 0f);
//
//        }
//    }
//
    public static void compileResults(String result){

        if (!result.isEmpty()) {
            detectedStrings.add(result);
        }
    }
//
//    private static Mat bufferedImageToMat(BufferedImage image) {
//        // Convert BufferedImage to Mat (OpenCV)
//        int width = image.getWidth();
//        int height = image.getHeight();
//        Mat mat = new Mat(height, width, CV_8UC3);
//        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
//        mat.data().put(data);
//        return mat;
//    }
//
//    public static BufferedImage matToBufferedImage(Mat mat) {
//        // Ensure the mat is in the correct format (8-bit, 3 channels, BGR)
//        if (mat.channels() == 1) {
//            // If it's grayscale, convert to 3 channels (BGR)
//            Mat matBGR = new Mat();
//            Imgproc.cvtColor(mat, matBGR, Imgproc.COLOR_BGR2GRAY);
//            mat = matBGR;
//        }
//
//        // Create a BufferedImage with the same dimensions as the mat
//        int width = mat.cols();
//        int height = mat.rows();
//        int channels = mat.channels();
//        int type = BufferedImage.TYPE_3BYTE_BGR;  // BGR format for OpenCV
//
//        // Create a new BufferedImage
//        BufferedImage image = new BufferedImage(width, height, type);
//
//        // Get the data from the mat and set it into the BufferedImage
//        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
//        mat.data().get(data);
//
//        return image;
//    }
}
