/***************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are subject to the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one
 at http://mozilla.org/MPL/2.0/.

 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 for the specific language governing rights and limitations under the License.

 Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.

 ******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.v4l;

import org.sensorhub.api.config.DisplayInfo;
import org.sensorhub.api.config.DisplayInfo.Required;
import org.sensorhub.api.sensor.PositionConfig;
import org.sensorhub.api.sensor.SensorConfig;
import org.sensorhub.api.sensor.PositionConfig.EulerOrientation;
import org.sensorhub.api.sensor.PositionConfig.LLALocation;


/**
 * <p>
 * Configuration class for the generic Video4Linux camera driver
 * </p>
 *
 * @author Alex Robin
 * @since Sep 6, 2013
 */
public class V4LCameraConfig extends SensorConfig
{
    @Required
    @DisplayInfo(desc="Camera serial number (used as suffix to generate unique identifier URI)")
    public String serialNumber = null;

    @DisplayInfo(desc="Name of video device to use (e.g. /dev/video0)")
    public String deviceName = "/dev/video0";

    @DisplayInfo(desc="Default camera params to use on startup. These can then be changed with the control interface")
    public V4LCameraParams defaultParams = new V4LCameraParams();

    @DisplayInfo(desc="Camera geographic position")
    public PositionConfig position = new PositionConfig();

    /*  Often it is necessary utilize libcamera to access and process the frames from the picam
        as /dev/video0 is not a user-facing device. This boolean should be switched on if a
        virtual camera is needed to access to picam video stream.
     */
    @Required
    @DisplayInfo(desc="Enable virtual camera using libcamera")
    public boolean virtualCamEnabled = true;

    @DisplayInfo(desc="Name of virtual cam device to use (e.g. /dev/video10)")
    public String virtualCam = "/dev/video10";

    @DisplayInfo(desc="Video codec to use (e.g. rawvideo, yuyv422, mjpeg )")
    public String vcodec = "mjpeg";

    @Override
    public LLALocation getLocation()
    {
        return position.location;
    }


    @Override
    public EulerOrientation getOrientation()
    {
        return position.orientation;
    }

}
