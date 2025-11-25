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
    String vcodec;
    String pix_format;
    String pix_format_convert;
    V4LCameraParams camParams;

    // sudo modprobe v4l2loopback devices=1 video_nr=12 card_label="VirtualCam" exclusive_caps=1
    
    // rpicam-vid -t 0 --width 1280 --height 960 --framerate 30 --codec yuv420 --output - | ffmpeg -f rawvideo -pix_fmt yuv420p -s 1280x960 -r 30 -i -     -vf format=rgb24,setsar=1:1     -f v4l2 -pix_fmt rgb24 /dev/video12


    public VirtualCam(String device, String virtualCam, String vcodec, String pix_format, String pix_format_convert, V4LCameraParams camParams) {

        this.device = device;
        this.virtualCam = virtualCam;
        this.vcodec = vcodec;
        this.pix_format = pix_format;
        this.pix_format_convert = pix_format_convert;
        this.camParams = camParams;

    }

    public void start() throws IOException, InterruptedException {

        // Extract video number from virtualCam string (e.g., "/dev/video12" -> "12")
        String videoNr = virtualCam.replaceAll("\\D", ""); // Remove all non-digits

        
        ProcessBuilder modprobePb = new ProcessBuilder(
            "sudo", "modprobe", "v4l2loopback",
            "devices=1", "video_nr=" + videoNr,
            "card_label=VirtualCam", "exclusive_caps=1"
        );
        modprobePb.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process modprobe = modprobePb.start();
        int modprobeExit = modprobe.waitFor();
        if (modprobeExit != 0) {
            throw new IOException("modprobe failed with exit code " + modprobeExit);
        }

        Thread.sleep(2000);


        ProcessBuilder rpicamPb = new ProcessBuilder(
            "rpicam-vid",
            "-t", "0",
            "--width", String.valueOf(camParams.imgWidth),
            "--height", String.valueOf(camParams.imgHeight),
            "--framerate", String.valueOf(camParams.frameRate),
            "--codec", "yuv420",
            "--output", "-"
        );

        ProcessBuilder ffmpegPb = new ProcessBuilder(
            "ffmpeg",
            "-f", "rawvideo",
            "-pix_fmt", "yuv420p",
            "-s", String.valueOf(camParams.imgWidth) + "x" + String.valueOf(camParams.imgHeight),
            "-r", String.valueOf(camParams.frameRate),
            "-i", "-",
            "-vf", "format=" + pix_format + ",setsar=1:1",
            "-f", "v4l2",
            "-pix_fmt", pix_format_convert,
            virtualCam
        );

        rpicamPb.redirectError(ProcessBuilder.Redirect.INHERIT);
        ffmpegPb.redirectError(ProcessBuilder.Redirect.INHERIT);
        
        // Start processes
        libCamera = rpicamPb.start();
        ffmpeg = ffmpegPb.start();
        
        // Step 4: Wait 2 more seconds
        Thread.sleep(2000);

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
        Thread.sleep(2000);

        pipeThread.start();
    }



        // ProcessBuilder libcameraPb = new ProcessBuilder(
        //         "libcamera-vid",
        //         "-t", "0",
        //         "--inline",
        //         "--width", "1280",
        //         "--height", "960",
        //         "--framerate", "30",
        //         "--codec", "yuv420",
        //         "--output", "-"
        // );

//        rpicam-vid -t 0 --width 1280 --height 960 --framerate 30 --codec yuv420 --output - |
//                ffmpeg -f rawvideo -pix_fmt bgr24 -s 1280x960 -r 30 -i -   -vf setsar=1:1
//                -f v4l2 -pix_fmt bgr24 /dev/video12

        // ProcessBuilder rpicamPb = new ProcessBuilder(
        //         "rpicam-vid",
        //         "-t", "0",
        //         "--inline",
        //         "--width", "1280",
        //         "--height", "960",
        //         "--framerate", "30",
        //         "--codec", "yuv420",
        //         "--output", "-"
        // );

//        ProcessBuilder ffmpegPb = new ProcessBuilder(
//                "ffmpeg",
//                "-f", "rawvideo",
//                "-pix_fmt", "yuv420p",
//                "-s", "1280x960",
//                "-r", "30",
//                "-i", "-",
////                "-vf", "scale=1280:960",   // upscale inside FFmpeg
//                "-b:v", "2M",              // higher bitrate = sharper output
//                "-vcodec", vcodec,
//                "-f", "v4l2",
//                virtualCam
//        );

//        ProcessBuilder ffmpegPb = new ProcessBuilder(
//                "ffmpeg",
//                "-f", "rawvideo",          // raw video input (no container)
//                "-pix_fmt", "yuv420p",     // pixel format of incoming frames
//                "-s", "1280x960",          // frame size
//                "-r", "30",                // frame rate
//                "-i", "-",                 // read from stdin (pipe)
//                "-pix_fmt", "yuv420p",     // keep same format for output
//                "-f", "v4l2",              // output to v4l2 device
//                virtualCam                 // e.g. "/dev/video10"
//        );
        // ProcessBuilder ffmpegPb = new ProcessBuilder(
        //         "ffmpeg",
        //         "-f", "rawvideo",
        //         "-pix_fmt", pix_format,
        //         "-s", "1280x960",
        //         "-r", "30",
        //         "-i", "-",
        //         "-pix_fmt", pix_format_convert,       // convert to BGR for v4l4j
        //         "-f", "v4l2",
        //         virtualCam
        // );


//        ProcessBuilder ffmpegPb = new ProcessBuilder(
//                "ffmpeg",
//                "-f", "rawvideo",
//                "-pix_fmt", "yuv420p",
//                "-s", "640x480",
//                "-r", "30",
//                "-i", "-",
//                "-vcodec", "rawvideo",
//                "-pix_fmt", "yuyv422",
//                "-f", "v4l2",
//                "/dev/video2"
//        );



        // ProcessBuilder ffmpegRawPb = new ProcessBuilder(
        //         "ffmpeg",
        //         "-f", "rawvideo",
        //         "-pix_fmt", "gray10le",   // for SRGGB10, or "yuyv422"/"rgb24" depending on Pi config
        //         "-s", "640x480",
        //         "-r", "30",
        //         "-i", "-",
        //         "-pix_fmt", "yuv420p",    // optional: convert to a more standard format
        //         "-vcodec", vcodec,
        //         "-f", "v4l2",
        //         virtualCam
        // );

        // libcameraPb.redirectError(ProcessBuilder.Redirect.INHERIT);
        // ffmpegPb.redirectError(ProcessBuilder.Redirect.INHERIT);

        // // Start processes
        // libCamera = libcameraPb.start();
        // ffmpeg = ffmpegPb.start();

        // // Pipe libcamera stdout → ffmpeg stdin
        // pipeThread = new Thread(() -> {
        //     try (
        //             InputStream camOut = libCamera.getInputStream();
        //             OutputStream ffmpegIn = ffmpeg.getOutputStream()
        //     ) {
        //         camOut.transferTo(ffmpegIn);
        //     } catch (IOException e) {
        //         e.printStackTrace();
        //     }
        // });
        // pipeThread.start();

        // Wait for processes if desired
//        int exitCode = ffmpeg.waitFor();
//        System.out.println("ffmpeg exited with code " + exitCode);

//    }

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

