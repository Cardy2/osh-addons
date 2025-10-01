package org.sensorhub.impl.sensor.v4l;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class VirtualCam {

    Process libCamera;
    Process ffmpeg;
    Thread pipeThread;
    String device;
    String virtualCam;

    public VirtualCam(String device, String virtualCam) {

        this.device = device;
        this.virtualCam = virtualCam;

    }

    public void start() throws IOException, InterruptedException {
//        String cmd =
//                "libcamera-vid -t 0 --inline --width 640 --height 480 --framerate 30 --codec yuv420 --output - | " +
//                        "ffmpeg -f rawvideo -pix_fmt yuv420p -s 640x480 -r 30 -i - " +
//                        "-vcodec mjpeg -f v4l2 /dev/video10";
//
//        ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd);
//        pb.inheritIO(); // optional, to see ffmpeg/libcamera logs
//        Process proc = pb.start();

        ProcessBuilder libcameraPb = new ProcessBuilder(
                "libcamera-vid",
                "-t", "0",
                "--inline",
                "--width", "640",
                "--height", "480",
                "--framerate", "30",
                "--codec", "yuv420",
                "--output", "-"
        );

        ProcessBuilder libcameraRawPb = new ProcessBuilder(
                "libcamera-raw",
                "-t", "0",                // run indefinitely
                "--width", "640",
                "--height", "480",
                "--framerate", "30",
                "--output", "-"           // write raw frames to stdout
        );

        ProcessBuilder ffmpegPb = new ProcessBuilder(
                "ffmpeg",
                "-f", "rawvideo",
                "-pix_fmt", "yuv420p",
                "-s", "640x480",
                "-r", "30",
                "-i", "-",
                "-vcodec", "mjpeg",
                "-f", "v4l2",
                virtualCam
        );

        ProcessBuilder ffmpegRawPb = new ProcessBuilder(
                "ffmpeg",
                "-f", "rawvideo",
                "-pix_fmt", "gray10le",   // for SRGGB10, or "yuyv422"/"rgb24" depending on Pi config
                "-s", "640x480",
                "-r", "30",
                "-i", "-",
                "-pix_fmt", "yuv420p",    // optional: convert to a more standard format
                "-vcodec", "mjpeg",
                "-f", "v4l2",
                virtualCam
        );

        libcameraPb.redirectError(ProcessBuilder.Redirect.INHERIT);
        ffmpegPb.redirectError(ProcessBuilder.Redirect.INHERIT);

        // Start processes
        libCamera = libcameraPb.start();
        ffmpeg = ffmpegPb.start();

        // Pipe libcamera stdout → ffmpeg stdin
        pipeThread = new Thread(() -> {
            try (
                    InputStream camOut = libCamera.getInputStream();
                    OutputStream ffmpegIn = ffmpeg.getOutputStream()
            ) {
                camOut.transferTo(ffmpegIn);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        pipeThread.start();

        // Wait for processes if desired
//        int exitCode = ffmpeg.waitFor();
//        System.out.println("ffmpeg exited with code " + exitCode);

    }

    public void stop() {
        if (pipeThread != null && pipeThread.isAlive()) {
            libCamera.destroy();
            ffmpeg.destroy();
        }
    }

    public boolean isRunning() {
        return pipeThread != null && pipeThread.isAlive();
    }
}

