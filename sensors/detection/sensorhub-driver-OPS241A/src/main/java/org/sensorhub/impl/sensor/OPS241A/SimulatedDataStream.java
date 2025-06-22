/***************************** BEGIN LICENSE BLOCK ***************************
 The contents of this file are subject to the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one
 at http://mozilla.org/MPL/2.0/.

 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 for the specific language governing rights and limitations under the License.

 Copyright (C) 2020-2025 Botts Innovative Research, Inc. All Rights Reserved.
 ******************************* END LICENSE BLOCK ***************************/
package org.sensorhub.impl.sensor.OPS241A;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimulatedDataStream implements Runnable {

    private final OPS241ASensor sensor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Random random = new Random();

    public SimulatedDataStream(OPS241ASensor sensor) {
        this.sensor = sensor;
    }

    public void start() {
        running.set(true);
        new Thread(this).start();
    }

    public void stop() {
        running.set(false);
    }

    @Override
    public void run() {
        while (running.get()) {
            // Generate fake radar values
//            int range = 100 + random.nextInt(500);      // 100cm to 600cm
            int velocity = -70 + random.nextInt(141); // -70 to 70 inclusive
//            int direction = (velocity >= 0) ? 1 : 0;     // 1 = approaching, 0 = receding

            String mockData = String.format("V=%d", velocity);

            sensor.ops241aOutput.SetData(velocity);

            try {
                Thread.sleep(200); // Simulate 5Hz data rate
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}