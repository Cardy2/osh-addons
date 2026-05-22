package org.sensorhub.impl.process.opencv;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

public class TemplateMatching {

    public static void main(String[] args) {
        // Load the OpenCV library
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        // Load the source image and template image
        Mat img = Imgcodecs.imread("C:/Users/cardy/Desktop/licensePlates/Alabama.jpg"); // Replace with your image path
        Mat template = Imgcodecs.imread("C:/Users/cardy/Desktop/socom/thresholded.jpg"); // Replace with your template path

        // Check if images are loaded correctly
        if (img.empty() || template.empty()) {
            System.out.println("Could not load images!");
            return;
        }

        // Create a matrix to store the result of the template matching
        Mat result = new Mat();

        // Perform template matching using TM_CCOEFF
        Imgproc.matchTemplate(img, template, result, Imgproc.TM_CCOEFF);

        // Find the location of the best match
        Core.MinMaxLocResult mmr = Core.minMaxLoc(result);

        // Get the top-left point of the rectangle to highlight the match
        Point matchLoc = mmr.maxLoc;

        // Draw a rectangle around the best match
        Rect rect = new Rect(matchLoc, new Size(template.cols(), template.rows()));
        Imgproc.rectangle(img, rect.tl(), rect.br(), new Scalar(0, 255, 0), 2);

        // Show the result image with a rectangle drawn around the match
        Imgcodecs.imwrite("matched_result.jpg", img); // Save the result as an image
        System.out.println("Template matched and result saved as 'matched_result.jpg'");
    }
}

