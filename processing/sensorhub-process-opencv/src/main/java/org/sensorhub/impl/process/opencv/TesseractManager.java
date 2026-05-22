package org.sensorhub.impl.process.opencv;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.tesseract.TessBaseAPI;

import java.io.*;
import java.nio.file.*;
import java.util.Comparator;

public class TesseractManager implements AutoCloseable {
    private TessBaseAPI api;
    private Path tessdataDir;

    public TesseractManager() throws IOException {
        prepareTessdata();
        initTesseract();
    }

    private void prepareTessdata() throws IOException {
        tessdataDir = Files.createTempDirectory("tessdata");
        Path trainedData = tessdataDir.resolve("eng.traineddata");

        try (InputStream in = getClass().getResourceAsStream("/tessdata/eng.traineddata")) {
            if (in == null)
                throw new FileNotFoundException("eng.traineddata not found in /resources/tessdata");

            Files.copy(in, trainedData, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void initTesseract() {
        api = new TessBaseAPI();
        if (api.Init(tessdataDir.toAbsolutePath().toString(), "eng") != 0)
            throw new RuntimeException("Could not initialize Tesseract");
    }

    public TessBaseAPI getApi() {
        return api;
    }

    public String recognize(org.bytedeco.opencv.opencv_core.Mat grayImage) {
        api.SetImage(grayImage.data(), grayImage.cols(), grayImage.rows(), 1, (int) grayImage.step());
        BytePointer result = api.GetUTF8Text();
        String text = result != null ? result.getString() : "";
        if (result != null) result.deallocate();
        return text;
    }

    @Override
    public void close() {
        if (api != null) {
            api.End();
            api.close();  // Frees native memory in JavaCPP
            api = null;
        }

        if (tessdataDir != null) {
            try {
                Files.walk(tessdataDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException ignored) {
            }
        }
    }
}
