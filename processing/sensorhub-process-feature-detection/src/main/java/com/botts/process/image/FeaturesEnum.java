/***************************** BEGIN LICENSE BLOCK ***************************

 Copyright (C) 2022 Botts Innovative Research, Inc. All Rights Reserved.

 ******************************* END LICENSE BLOCK ***************************/
package com.botts.process.image;

/**
 * An enumeration of the features supported for feature detection in images
 *
 * @author Nick Garay
 * @since 1.0.0
 */
public enum FeaturesEnum {

    FACES("Faces", "/classifiers/haarcascade_frontalface_alt.xml"),
    PROFILES("Faces - Profile View", "/classifiers/haarcascade_profileface.xml"),
    HANDS("Hands", "/classifiers/haarcascade_hands.xml"),
    EYES("Eyes", "/classifiers/haarcascade_eye.xml"),
    LEFT_EYE("Left Eye", "/classifiers/haarcascade_lefteye_2splits.xml"),
    RIGHT_EYE("Right Eye", "/classifiers/haarcascade_righteye_2splits.xml"),
    EYE_GLASSES("Eyeglasses", "/classifiers/haarcascade_eye_tree_eyeglasses.xml"),
    SMILES("Smiles", "/classifiers/haarcascade_smile.xml"),
    FULL_BODY("Full Body", "/classifiers/haarcascade_fullbody.xml"),
    UPPER_BODY("Upper Body", "/classifiers/haarcascade_upperbody.xml"),
    LOWER_BODY("Lower Body", "/haarcascade_eye_lowerbody.xml"),
    PEDESTRIANS("Pedestrians", "/classifiers/haarcascade_pedestrian.xml"),
    CARS("Cars", "/classifiers/haarcascade_cars.xml"),
    BUSES_FRONT("Buses", "/classifiers/haarcascade_bus_front.xml"),
    BUSES_TWO_WHEELERS("Two Wheelers", "/classifiers/haarcascade_two_wheeler.xml");

    String label;
    String resource;

    /**
     * Constructor
     *
     * @param label The label to apply to the feature enumerated value
     * @param resource the resource property of this enumerated value
     */
    FeaturesEnum(String label, String resource) {

        this.label = label;
        this.resource = resource;
    }

    /**
     * Returns the resource property of this enumerated value
     *
     * @return the resource property of this enumerated value
     */
    public String getResource() {

        return resource;
    }

    @Override
    public String toString() {

        return label;
    }
}
