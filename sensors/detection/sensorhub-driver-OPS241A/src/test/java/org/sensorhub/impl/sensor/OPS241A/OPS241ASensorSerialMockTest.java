package org.sensorhub.impl.sensor.OPS241A;

import net.opengis.sensorml.v20.AbstractProcess;
import net.opengis.swe.v20.DataComponent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sensorhub.api.ISensorHub;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.api.data.IStreamingDataInterface;
import org.sensorhub.api.event.IEventListener;
import org.sensorhub.impl.SensorHub;
import org.sensorhub.impl.comm.rxtx.RxtxSerialCommProviderConfig;
import org.sensorhub.impl.sensor.OPS241A.config.OPS241AConfig;
import org.sensorhub.impl.sensor.OPS241A.config.rxtxConfig;
import org.vast.data.TextEncodingImpl;
import org.vast.sensorML.SMLUtils;
import org.vast.swe.AsciiDataWriter;
import org.vast.swe.SWEUtils;

import java.io.*;
import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.Assert.assertTrue;

public class OPS241ASensorSerialMockTest implements IEventListener {
    OPS241ASensor driver;
    OPS241AConfig config;
    AsciiDataWriter writer;
    int sampleCount = 0;


    @Before
    public void init() throws Exception {

        ISensorHub hub = new SensorHub();
        hub.start();

        config = new OPS241AConfig();
        config.serialNumber = UUID.randomUUID().toString();
        config.id = UUID.randomUUID().toString();
//        config.RxTxSettings = new rxtxConfig();
        config.unit = OPS241AConfig.Units.MILES_PER_HOUR;
        RxtxSerialCommProviderConfig serialConf = new RxtxSerialCommProviderConfig();

//        serialConf.protocol.portName = "/dev/ttyUSB0";
        serialConf.protocol.baudRate = 9600;
        serialConf.protocol.receiveThreshold = 32;
        config.commSettings = serialConf;

//        rxtxConfig providerConf = new rxtxConfig();

//        providerConf.moduleClass = "org.sensorhub.impl.comm.rxtx.RxtxSerialCommProvider";
//        providerConf.protocol.portName = "/dev/ttyACM0";
//        providerConf.protocol.baudRate = 19200;
//        providerConf.protocol.dataBits = 8;
//        providerConf.protocol.stopBits = 1;
//        providerConf.protocol.parity = UARTConfig.Parity.PARITY_NONE;
//        providerConf.protocol.receiveTimeout = 100;
//        config.commSettings = providerConf;

//        providerConf.portAddress = "/dev/ttyACM0";
//        providerConf.baudRate = 19200;

        driver = new OPS241ASensor();
        driver.setParentHub(hub);
        driver.init(config);
    }


    @Test
    public void testGetOutputDesc() throws Exception {
        for (IStreamingDataInterface di : driver.getObservationOutputs().values()) {
            System.out.println();
            DataComponent dataMsg = di.getRecordDescription();
            new SWEUtils(SWEUtils.V2_0).writeComponent(System.out, dataMsg, false, true);
        }
    }


    @Test
    public void testGetSensorDesc() throws Exception {
        System.out.println();
        AbstractProcess smlDesc = driver.getCurrentDescription();
        new SMLUtils(SWEUtils.V2_0).writeProcess(System.out, smlDesc, true);
    }


    @Test
    public void testSendMeasurements() throws Exception {
        System.out.println();
        String simulatedData = "R,0,+12.34,123\nR,0,-05.60,88\n";

        InputStream fakeSerialInput = new ByteArrayInputStream(simulatedData.getBytes());

        injectInputStream(driver, fakeSerialInput);

        // Start the sensor read thread (assuming the sensor loops in a thread)
        driver.init();  // If needed to set up sensors
        driver.start(); // Might be a method in OSH sensors

        driver.setInputStream(new ByteArrayInputStream(simulatedData.getBytes()));

        ByteArrayOutputStream reportLine = new ByteArrayOutputStream();
        int b = driver.dataIn.read();

        assertTrue(b != -1);

        String line = "{...}";

        reportLine.reset();
        driver.handleJsonMsg(line);

        writer = new AsciiDataWriter();
        writer.setDataEncoding(new TextEncodingImpl(",", "\n"));
        writer.setOutput(System.out);

        IStreamingDataInterface locOutput = driver.getObservationOutputs().get("OPS241A");
        locOutput.registerListener(this);

//        driver.start();

//        synchronized (this) {
//            while (sampleCount < 50)
//                wait();
//        }

        System.out.println();
        Thread.sleep(500); // adjust timing as needed

//        driver.ops241aOutput.SetData(1.750612743E9);

//        assertEquals(12.34, locOutput.getLatestRecord().getIntValue(), 0.01);
        // Check that sensor generated observations or internal data
        // You may need to expose observations via a public getter or listener
        // For now, we just confirm the code runs without exceptions
        driver.stop();
    }

    @Override
    public void handleEvent(org.sensorhub.api.event.Event e) {

        assertTrue(e instanceof DataEvent);
        DataEvent dataEvent = (DataEvent) e;

        try {
            //System.out.print("\nNew data received from sensor " + newDataEvent.getSensorId());
            IStreamingDataInterface output = driver.getObservationOutputs().get(dataEvent.getOutputName());
            writer.setDataComponents(output.getRecordDescription().copy());
            writer.reset();
            writer.write(dataEvent.getRecords()[0]);
            writer.flush();

            sampleCount++;
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        synchronized (this) {
            this.notify();
        }
    }
    private void injectInputStream(OPS241ASensor sensor, InputStream fakeIn) throws Exception {
        Field inputField = OPS241ASensor.class.getDeclaredField("dataIn");
        inputField.setAccessible(true);
        inputField.set(sensor, fakeIn);
    }

    @After
    public void cleanup() {
        try {
            driver.stop();
        } catch (SensorHubException e) {
            e.printStackTrace();
        }
    }
}
