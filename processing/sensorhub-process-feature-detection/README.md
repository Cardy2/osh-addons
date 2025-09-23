# Feature Detection Image Processing

This is an executable process based performing feature detection on video streams.
The features to detect currently supported are:

    FACES
    PROFILES
    HANDS
    EYES
    LEFT_EYE
    RIGHT_EYE
    EYE_GLASSES
    SMILES
    FULL_BODY
    UPPER_BODY
    LOWER_BODY
    PEDESTRIANS
    CARS
    BUSES_FRONT
    BUSES_TWO_WHEELERS

and are based on publicly available open source Haar Cascade classifier models.

## Configuration

Configuring the sensor requires providing a sensorML processchain-*.xml document

These documents should be contained in _sensorML_ directory within the deployed instance of OpenSensorHub

## Sample Configuration

**./sensorML/processchain-feature-detect.xml**

    <sml:AggregateProcess xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:sml="http://www.opengis.net/sensorml/2.0" xmlns:swe="http://www.opengis.net/swe/2.0" xmlns:gml="http://www.opengis.net/gml/3.2" xmlns:gco="http://www.isotc211.org/2005/gco" xmlns:gmd="http://www.isotc211.org/2005/gmd" gml:id="CHAIN1">
      <gml:identifier codeSpace="uid">0c58ed65-e63d-4d1e-9ef0-b7db0be3685c</gml:identifier>
      <sml:components>
        <sml:ComponentList>
          <sml:component name="source0">
            <sml:SimpleProcess gml:id="P1">
              <gml:identifier codeSpace="uid">urn:osh:sensor:uas:uas001</gml:identifier>
              <sml:typeOf xlink:href="urn:osh:process:datasource:stream"/>
              <sml:configuration>
                <sml:Settings>
                  <sml:setValue ref="parameters/producerURI">urn:osh:sensor:uas:uas001</sml:setValue>
                </sml:Settings>
              </sml:configuration>
            </sml:SimpleProcess>
          </sml:component>
          <sml:component name="decoder">
            <sml:SimpleProcess gml:id="P2">
              <sml:typeOf xlink:href="urn:osh:process:opencv:FeatureDetection"/>
              <sml:configuration>
                <sml:Settings>
                  <sml:setValue ref="inputs/imageFrame/width">720</sml:setValue>
                  <sml:setValue ref="inputs/imageFrame/height">480</sml:setValue>
                  <sml:setValue ref="parameters/feature">CARS</sml:setValue>
                </sml:Settings>
              </sml:configuration>
            </sml:SimpleProcess>
          </sml:component>
        </sml:ComponentList>
      </sml:components>
      <sml:outputs>
        <sml:OutputList>
          <sml:output name="video">
            <swe:DataRecord definition="http://sensorml.com/ont/swe/property/VideoFrame">
              <swe:label>Detected Features</swe:label>
              <swe:description>Processed image frames displaying detected features</swe:description>
              <swe:field name="time">
                <swe:Time definition="http://www.opengis.net/def/property/OGC/0/SamplingTime"
                          referenceFrame="http://www.opengis.net/def/trs/BIPM/0/UTC">
                  <swe:label>Sampling Time</swe:label>
                  <swe:uom xlink:href="http://www.opengis.net/def/uom/ISO-8601/0/Gregorian"/>
                </swe:Time>
              </swe:field>
              <swe:field name="img">
                <swe:DataArray definition="http://sensorml.com/ont/swe/property/RasterImage">
                  <swe:elementCount>
                    <swe:Count definition="http://sensorml.com/ont/swe/property/GridHeight" axisID="Y">
                      <swe:value>480</swe:value>
                    </swe:Count>
                  </swe:elementCount>
                  <swe:elementType name="row">
                    <swe:DataArray>
                      <swe:elementCount>
                        <swe:Count definition="http://sensorml.com/ont/swe/property/GridWidth" axisID="X">
                          <swe:value>640</swe:value>
                        </swe:Count>
                      </swe:elementCount>
                      <swe:elementType name="pixel">
                        <swe:DataRecord>
                          <swe:field name="red">
                            <swe:Count definition="http://sensorml.com/ont/swe/property/RedChannel"/>
                          </swe:field>
                          <swe:field name="green">
                            <swe:Count definition="http://sensorml.com/ont/swe/property/GreenChannel"/>
                          </swe:field>
                          <swe:field name="blue">
                            <swe:Count definition="http://sensorml.com/ont/swe/property/BlueChannel"/>
                          </swe:field>
                        </swe:DataRecord>
                      </swe:elementType>
                    </swe:DataArray>
                  </swe:elementType>
                </swe:DataArray>
              </swe:field>
            </swe:DataRecord>
          </sml:output>
        </sml:OutputList>
      </sml:outputs>
      <sml:connections>
        <sml:ConnectionList>
          <sml:connection>
            <sml:Link>
              <sml:source ref="components/source0/outputs/video/img"/>
              <sml:destination ref="components/decoder/inputs/imageFrame/img"/>
            </sml:Link>
          </sml:connection>
          <sml:connection>
            <sml:Link>
              <sml:source ref="components/source0/outputs/video/time"/>
              <sml:destination ref="components/decoder/inputs/imageFrame/time"/>
            </sml:Link>
          </sml:connection>
          <sml:connection>
            <sml:Link>
              <sml:source ref="components/decoder/inputs/imageFrame/img"/>
              <sml:destination ref="outputs/imageFrame/img"/>
            </sml:Link>
          </sml:connection>
          <sml:connection>
            <sml:Link>
              <sml:source ref="components/decoder/inputs/imageFrame/time"/>
              <sml:destination ref="outputs/video/time"/>
            </sml:Link>
          </sml:connection>
        </sml:ConnectionList>
      </sml:connections>
    </sml:AggregateProcess>