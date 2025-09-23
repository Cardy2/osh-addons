/***************************** BEGIN LICENSE BLOCK ***************************

 Copyright (C) 2022 Botts Innovative Research, Inc. All Rights Reserved.

 ******************************* END LICENSE BLOCK ***************************/
package com.botts.process.image;

import org.sensorhub.api.module.IModule;
import org.sensorhub.api.module.IModuleProvider;
import org.sensorhub.api.module.ModuleConfig;
import org.sensorhub.impl.module.JarModuleProvider;
import org.sensorhub.impl.processing.AbstractProcessProvider;

/**
 * Descriptor for the {@link FeatureDetectionProcess}
 *
 * @author Nick Garay
 * @since 1.0.0
 */
public class FeatureDetectionDescriptor extends AbstractProcessProvider
{
    public FeatureDetectionDescriptor()
    {
        addImpl(FeatureDetectionProcess.INFO);
    }
}
