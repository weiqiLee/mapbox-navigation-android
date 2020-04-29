package com.mapbox.navigation.ui.map;

import android.graphics.Color;

import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.style.layers.FillExtrusionLayer;

import org.junit.Test;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertSame;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class BuildingExtrusionLayerTest {

  @Test
  public void buildingExtrusionLayerVisibilityFalse() {
    BuildingExtrusionLayer buildingExtrusionLayer = mock(BuildingExtrusionLayer.class);
    buildingExtrusionLayer.updateVisibility(false);
    assertFalse(buildingExtrusionLayer.getVisibility());
  }
}