# OpenCV Vehicle Recognition

## How to Build
- Make sure to include this process, `sensors/detection/sensorhub-driver-OPS241A`, &
`osh-addons/sensors/video/sensorhub-driver-v4l` in project-level settings.gradle and build.gradle

## Setting Up the RasPi 5
- Add SSH Key
- Configure hotspot:
      - Go to Wireless Connections -> Advanced Options -> Edit Connections
      - Add Wireless Hotspot - Name: speed0$; PW: speed0$
      - Security WPA/WPA2/WPA3 Personal
- Install Java 21:
      - sudo apt install default-jre-headless
      - sudo apt install openjdk-21-jdk-headless
      - export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64

- Install Dependencies:
      - sudo apt update
      - sudo apt install v4l2loopback-dkms ffmpeg libcamera-apps 

## Adding the Native Libraries
- copy the `libvideo.so` & `libv4l4j.so` files from:

`osh-addons/sensors/video/sensorhub-driver-v4l/src/main/resources/lib/native/linux/linux-aarch64`

- paste them into the `usr/lib` directory on the Pi:
  - from the command line you can navigate to the location of the files and run:
  - `sudo cp libvideo.so /usr/lib`
  - `sudo cp libv4l4j.so /usr/lib`


## Running the Process
- The Speed Trap process requires the V4L Driver & OPS241A Driver

### V4L Driver
#### General Configurations 
- In order to access the pi cam, a virtual camera must be enabled in modern Pi operating systems
- Once the driver is added, ensure the `Virtual Cam Enabled` box is checked 
- Set the serial number to `001` (this is the value set in the process)
- Other video formatting options are configurable but the default values should work properly for this use-case
- `Device Name` is the port for the camera - almost always `/dev/video0`
- `Virtual Cam` is the port where the camera is streamed to 

#### Initializing & Starting
- Once the configuration are set, click `apply changes` to initialize the driver
- The initialization will fail the 1st time since the virtual cam doesn't yet exist
- Click `apply changes` again & it will start streaming to the virtual cam
- Start the driver


### OPS241A Driver 
#### General Configurations
- Set the serial number to `001` (this is the value set in the process)
- For testing, there is a `sim mode` that produces random velocities between -70 & 70
- If connecting to the device itself, ensure the `sim mode` checkbox is unchecked
- ... RX/TX Connection needs debugging 


### Speed Trap Process
- Navigate to the `Processing` tab in the admin panel
- Right click to `Add New Module` -> `New SensorML Stream Process`
- In the general configuration, input `process-speedtrap.xml` as the `SensorML File`
- `Apply Changes`, then start the module
- The observation outputs won't be updated until a vehicle is detected
