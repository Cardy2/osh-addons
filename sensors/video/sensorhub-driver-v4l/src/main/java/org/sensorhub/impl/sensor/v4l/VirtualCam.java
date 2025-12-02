package org.sensorhub.impl.sensor.v4l;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualCam {

    Process libCamera;
    Process ffmpeg;
    Thread pipeThread;
    Thread monitorThread;
    String device;
    String virtualCam;
    String vcodec;
    String pix_format;
    String pix_format_convert;
    V4LCameraParams camParams;
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger restartCount = new AtomicInteger(0);
    private static final int MAX_RESTARTS = 5;
    private static final long RESTART_DELAY_MS = 5000; // 5 seconds between restarts

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
        running.set(true);
        
        // Extract video number from virtualCam string (e.g., "/dev/video12" -> "12")
        String videoNr = virtualCam.replaceAll("\\D", "");

        // Setup v4l2loopback
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

        // Start processes
        startCameraProcesses();
        
        // Start monitor thread to check if processes are still alive
        monitorThread = new Thread(() -> {
            monitorProcesses();
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    private void startCameraProcesses() throws IOException, InterruptedException {
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

        // Clean up old processes if they exist
        if (libCamera != null) {
            libCamera.destroyForcibly();
        }
        if (ffmpeg != null) {
            ffmpeg.destroyForcibly();
        }

        // Start processes
        libCamera = rpicamPb.start();
        ffmpeg = ffmpegPb.start();

        Thread.sleep(2000); // Give processes time to initialize

        // Start pipe thread
        if (pipeThread != null && pipeThread.isAlive()) {
            pipeThread.interrupt();
        }
        
        pipeThread = new Thread(() -> {
            try (
                InputStream camOut = libCamera.getInputStream();
                OutputStream ffmpegIn = ffmpeg.getOutputStream()
            ) {
                camOut.transferTo(ffmpegIn);
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("Error piping camera data: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        pipeThread.setDaemon(true);
        pipeThread.start();
    }

    private void monitorProcesses() {
        while (running.get()) {
            try {
                Thread.sleep(2000); // Check every 2 seconds
                
                boolean needsRestart = false;
                
                // Check if rpicam-vid is still alive
                if (libCamera != null && !libCamera.isAlive()) {
                    int exitCode = libCamera.exitValue();
                    System.err.println("rpicam-vid process died with exit code: " + exitCode);
                    needsRestart = true;
                }
                
                // Check if ffmpeg is still alive
                if (ffmpeg != null && !ffmpeg.isAlive()) {
                    int exitCode = ffmpeg.exitValue();
                    System.err.println("ffmpeg process died with exit code: " + exitCode);
                    needsRestart = true;
                }
                
                // Restart if needed
                if (needsRestart && running.get()) {
                    int restarts = restartCount.incrementAndGet();
                    if (restarts > MAX_RESTARTS) {
                        System.err.println("Max restart attempts (" + MAX_RESTARTS + ") reached. Stopping camera.");
                        running.set(false);
                        break;
                    }
                    
                    System.err.println("Attempting to restart camera processes (attempt " + restarts + "/" + MAX_RESTARTS + ")...");
                    
                    // Clean up old processes
                    try {
                        if (libCamera != null) libCamera.destroyForcibly();
                        if (ffmpeg != null) ffmpeg.destroyForcibly();
                        if (pipeThread != null && pipeThread.isAlive()) {
                            pipeThread.interrupt();
                            pipeThread.join(1000);
                        }
                    } catch (Exception e) {
                        System.err.println("Error cleaning up processes: " + e.getMessage());
                    }
                    
                    // Wait before restarting
                    Thread.sleep(RESTART_DELAY_MS);
                    
                    // Restart processes
                    try {
                        startCameraProcesses();
                        System.err.println("Camera processes restarted successfully.");
                        restartCount.set(0); // Reset counter on successful restart
                    } catch (Exception e) {
                        System.err.println("Failed to restart camera processes: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else if (!needsRestart) {
                    restartCount.set(0); // Reset counter if processes are healthy
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error in monitor thread: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        running.set(false);
        
        if (monitorThread != null && monitorThread.isAlive()) {
            monitorThread.interrupt();
            try {
                monitorThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        if (pipeThread != null && pipeThread.isAlive()) {
            pipeThread.interrupt();
            try {
                pipeThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        if (libCamera != null) {
            libCamera.destroyForcibly();
            try {
                libCamera.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        if (ffmpeg != null) {
            ffmpeg.destroyForcibly();
            try {
                ffmpeg.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isRunning() {
        return running.get() && 
               libCamera != null && libCamera.isAlive() && 
               ffmpeg != null && ffmpeg.isAlive();
    }
}

