# OpenCV Vehicle Recognition

## How to Build
- Make sure to include this process and `osh-addons/sensors/video/sensorhub-driver-v4l` in project-level settings.gradle and build.gradle

## Setting Up the RasPi 5
- 


- sudo apt install openjdk-17-jdk or 11
- export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64

- sudo apt update
- sudo apt install v4l2loopback-dkms ffmpeg libcamera-apps default-jdk


## Process Chain Description
- This process can build its own SensorML description! 
- All you need to do is include/run the tests in `sensorhub-process-fakeweather/src/test/java/ProcessDescriptionGenerator.java`
- The output of the XML and JSON descriptions will appear in your terminal/console log.
- Put this description in a `*.xml` or `*.json` file (ex: `weather-process.json`)

## How to Deploy
- Launch OSH node
- Add and start a Simulated Weather Sensor driver
- Add a SensorML Stream Process module under Processing
- Put the path of the SensorML process description in the process module's configuration
- Start the process module. You should see an output appear at the bottom of the module's configuration menu

## Common Issues
- You may need to change names or uids of parts of the process to get it to work. For example, you may need to specify your Simulated Weather Sensor's system UID in the process description. You should get helpful errors when starting the process module.
- If you are trying to run a JSON process description, you may receive an error similar to "error: expected < but found { ." This simply means you need to update your osh-core to the latest version/commit.