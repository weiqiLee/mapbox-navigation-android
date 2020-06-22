package com.mapbox.navigation.ui.internal.building;

import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.Log;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.Polygon;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.expressions.Expression;
import com.mapbox.mapboxsdk.style.layers.FillExtrusionLayer;
import com.mapbox.mapboxsdk.style.layers.FillLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.mapboxsdk.style.sources.VectorSource;

import java.util.List;

import androidx.annotation.NonNull;

import static com.mapbox.mapboxsdk.style.expressions.Expression.all;
import static com.mapbox.mapboxsdk.style.expressions.Expression.eq;
import static com.mapbox.mapboxsdk.style.expressions.Expression.get;
import static com.mapbox.mapboxsdk.style.expressions.Expression.id;
import static com.mapbox.mapboxsdk.style.expressions.Expression.literal;
import static com.mapbox.mapboxsdk.style.expressions.Expression.match;
import static com.mapbox.mapboxsdk.style.layers.Property.NONE;
import static com.mapbox.mapboxsdk.style.layers.Property.VISIBLE;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillExtrusionColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillExtrusionHeight;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillExtrusionOpacity;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillOpacity;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.visibility;

/**
 * This layer handles the creation and customization of a {@link FillLayer}
 * to highlight the footprint of an individual building. For now, this layer is only
 * compatible with the Mapbox Streets v8 vector tile source. See
 * [https://docs.mapbox.com/vector-tiles/reference/mapbox-streets-v8/]
 * (https://docs.mapbox.com/vector-tiles/reference/mapbox-streets-v8/) for more information
 * about the Mapbox Streets v8 vector tile source.
 */
public class BuildingHighlightLayer {

  public static final String TAG = "BuildingHighlightLayer";
  public static final String BUILDING_FOOTPRINT_LAYER_ID = "building-footprint-layer-id";
  public static final String BUILDING_HIGHLIGHTED_EXTRUSION_LAYER_ID = "building-extrusion-highlighted-layer-id";
  private static final String BUILDING_FOOTPRINT_SOURCE_ID = "building-footprint-source-id";
  private static final String BUILDING_VECTOR_SOURCE_ID = "building-vector-source-id";
  private static final String BUILDING_LAYER_ID = "building";
  private static final String BUILDING_STATION_LAYER_ID = "building station";
  private static final Integer DEFAULT_FOOTPRINT_COLOR = Color.RED;
  private static final Float DEFAULT_BUILDING_FOOTPRINT_OPACITY = 1f;
  private final MapboxMap mapboxMap;
  private Polygon buildingPolygon;
  private Feature buildingPolygonFeature;
  private Integer color;
  private Float opacity;
  private String buildingId;

  public BuildingHighlightLayer(MapboxMap mapboxMap) {
    this.mapboxMap = mapboxMap;
  }

  /**
   * Toggles the visibility of the building footprint highlight layer.
   *
   * @param visible true if the layer should be displayed. False if it should be hidden.
   */
  public void updateSingleFootprintHighlightVisibility(final boolean visible) {
    mapboxMap.getStyle(new Style.OnStyleLoaded() {
      @Override
      public void onStyleLoaded(@NonNull Style style) {
        FillLayer buildingFootprintFillLayer = style.getLayerAs(BUILDING_FOOTPRINT_LAYER_ID);
        if (buildingFootprintFillLayer == null) {
          addFootprintHighlightFillLayerToMap();
        } else if ((buildingFootprintFillLayer.getVisibility().value.equals(VISIBLE)) != visible) {
          buildingFootprintFillLayer.setProperties(visibility(NONE));
        }
      }
    });
  }

  /**
   * Toggles the visibility of the highlighted extrusion layer.
   *
   * @param visible true if the layer should be displayed. False if it should be hidden.
   */
  public void updateSingleExtrusionHighlightVisibility(final boolean visible) {
    mapboxMap.getStyle(new Style.OnStyleLoaded() {
      @Override
      public void onStyleLoaded(@NonNull Style style) {
        FillExtrusionLayer buildingExtrusionLayer = style.getLayerAs(BUILDING_HIGHLIGHTED_EXTRUSION_LAYER_ID);
        if (buildingExtrusionLayer == null) {
          addHighlightExtrusionLayerToMap();
        } else if ((buildingExtrusionLayer.getVisibility().value.equals(VISIBLE)) != visible) {
          buildingExtrusionLayer.setProperties(visibility(NONE));
        }
      }
    });
  }

  /**
   * Set the {@link LatLng} location of the building highlight layer. The {@link LatLng} passed
   * through this method is used to see whether its within the footprint of a specific
   * building. If so, that building's footprint is used for a 2D highlighted footprint
   * or a 3D highlighted extrusion.
   *
   * @param targetLatLng the new coordinates to use in querying the building layer
   *                     to get the associated {@link Polygon} to eventually highlight.
   */
  public void setQueryLatLng(final LatLng targetLatLng) {
    mapboxMap.getStyle(new Style.OnStyleLoaded() {
      @Override
      public void onStyleLoaded(@NonNull Style style) {
//        GeoJsonSource geoJsonSource = style.getSourceAs(BUILDING_FOOTPRINT_SOURCE_ID);
        Feature buildingPolygonFeature = getFeatureAssociatedWithBuildingFootprint(targetLatLng);
        Log.d(TAG, "buildingPolygonFeature = " + buildingPolygonFeature.toJson());
        Log.d(TAG, "buildingPolygonFeature.id() = " + buildingPolygonFeature.id());
        buildingId = buildingPolygonFeature.id();
        VectorSource buildingFootprintVectorSource = style.getSourceAs(BUILDING_VECTOR_SOURCE_ID);
        if (buildingFootprintVectorSource != null) {

          List<Feature> featureList = buildingFootprintVectorSource.querySourceFeatures(new String[]{BUILDING_LAYER_ID},
              all(id(), literal(buildingId)));
          Log.d(TAG, "onStyleLoaded: featureList size = " + featureList.size());

          PointF pixel = mapboxMap.getProjection().toScreenLocation(new LatLng(
              targetLatLng.getLatitude(), targetLatLng.getLongitude()
          ));
          List<Feature> renderedFeatures = mapboxMap.queryRenderedFeatures(pixel, BUILDING_LAYER_ID, BUILDING_STATION_LAYER_ID);
          Log.d(TAG, "onStyleLoaded: renderedFeatures size = " + renderedFeatures.size());

          Log.d(TAG, "onStyleLoaded: featureList size = " + featureList.size());
          for (Feature singleFeature : featureList) {
            Log.d(TAG, "onStyleLoaded: featureList singleFeature id = " + singleFeature.id());
          }
        }
        /*FillLayer buildingFootprintFillLayer = style.getLayerAs(BUILDING_FOOTPRINT_LAYER_ID);
        if (buildingFootprintFillLayer != null) {
          buildingFootprintFillLayer.setFilter(all(id(), literal(buildingId)));
        }*/
        FillExtrusionLayer buildingFillExtrusionLayer = style.getLayerAs(BUILDING_HIGHLIGHTED_EXTRUSION_LAYER_ID);
        if (buildingFillExtrusionLayer != null) {
          // TODO: Try making buildingId an int/number
          Log.d(TAG, "onStyleLoaded: buildingId = " + buildingId);
          Log.d(TAG, "onStyleLoaded: id().toString() = " + id().toString());
//          buildingFillExtrusionLayer.setFilter(all(eq(id(), literal(buildingId))));
/*
          buildingFillExtrusionLayer.setFilter(
              match(id(), all(
                  literal(buildingId)),
                  literal(false), literal(true)
              )
          );
*/
        }
      }
    });
  }


  /**
   * Set the color of the building highlight layer.
   *
   * @param newFootprintColor the new color value
   */
  public void setColor(final int newFootprintColor) {
    mapboxMap.getStyle(new Style.OnStyleLoaded() {
      @Override
      public void onStyleLoaded(@NonNull Style style) {
        FillLayer buildingFootprintFillLayer = style.getLayerAs(BUILDING_FOOTPRINT_LAYER_ID);
        if (buildingFootprintFillLayer != null) {
          buildingFootprintFillLayer.setProperties(fillColor(newFootprintColor));
        }
        FillExtrusionLayer buildingFillExtrusionLayer = style.getLayerAs(BUILDING_HIGHLIGHTED_EXTRUSION_LAYER_ID);
        if (buildingFillExtrusionLayer != null) {
          buildingFillExtrusionLayer.setProperties(fillExtrusionColor(newFootprintColor));
        }
        color = newFootprintColor;
      }
    });
  }

  /**
   * Set the opacity of the building highlight layer.
   *
   * @param newFootprintOpacity the new opacity value
   */
  public void setOpacity(final Float newFootprintOpacity) {
    mapboxMap.getStyle(new Style.OnStyleLoaded() {
      @Override
      public void onStyleLoaded(@NonNull Style style) {
        FillLayer buildingFootprintFillLayer = style.getLayerAs(BUILDING_FOOTPRINT_LAYER_ID);
        if (buildingFootprintFillLayer != null) {
          buildingFootprintFillLayer.setProperties(fillOpacity(newFootprintOpacity));
        }
        FillExtrusionLayer buildingFillExtrusionLayer = style.getLayerAs(BUILDING_HIGHLIGHTED_EXTRUSION_LAYER_ID);
        if (buildingFillExtrusionLayer != null) {
          buildingFillExtrusionLayer.setProperties(fillExtrusionOpacity(newFootprintOpacity));
        }
        opacity = newFootprintOpacity;
      }
    });
  }

  /**
   * Retrieve the {@link Polygon} geometry of the footprint of the building that's associated with
   * the latest targetLatLng.
   *
   * @return the {@link Polygon}
   */
  public Polygon getBuildingPolygon() {
    return buildingPolygon;
  }

  /**
   *
   * Retrieve the {@link Feature} the polygonal footprint of the building that's associated with
   * the latest targetLatLng.
   *
   * @return the {@link Feature}
   */
  public Feature getBuildingPolygonFeature() {
    return buildingPolygonFeature;
  }

  /**
   * Retrieve the latest set color of the building highlight layer.
   *
   * @return the color Integer
   */
  public Integer getColor() {
    return color;
  }

  /**
   * Retrieve the latest set opacity of the building highlight layer.
   *
   * @return the opacity Float
   */
  public Float getOpacity() {
    return opacity;
  }

  /**
   * Retrieve a
   *
   * @param targetLatLng the {@link LatLng} that's used to see whether its within the
   *                     footprint of a specific building
   * @return a {@link Polygon} geometry
   */
  private Feature getFeatureAssociatedWithBuildingFootprint(final LatLng targetLatLng) {
    mapboxMap.getStyle(new Style.OnStyleLoaded() {
      @Override
      public void onStyleLoaded(@NonNull Style style) {
        PointF pixel = mapboxMap.getProjection().toScreenLocation(new LatLng(
            targetLatLng.getLatitude(), targetLatLng.getLongitude()
        ));
        if (style.getLayer(BUILDING_LAYER_ID) != null) {
          List<Feature> features = mapboxMap.queryRenderedFeatures(pixel, BUILDING_LAYER_ID, BUILDING_STATION_LAYER_ID);
          if (features.size() > 0 && features.get(0).geometry() instanceof Polygon) {
              buildingPolygonFeature = features.get(0);
              buildingPolygon = (Polygon) buildingPolygonFeature.geometry();
          }
        }
      }
    });
    return buildingPolygonFeature;
  }

  /**
   * Customize and add a {@link FillLayer} to the map to show a highlighted
   * building footprint.
   */
  private void addFootprintHighlightFillLayerToMap() {
    mapboxMap.getStyle(new Style.OnStyleLoaded() {
      @Override
      public void onStyleLoaded(@NonNull Style style) {
        FillLayer existingBuildingLayerId = style.getLayerAs(BUILDING_LAYER_ID);
        if (existingBuildingLayerId != null) {
          GeoJsonSource buildingFootprintGeoJsonSource = new GeoJsonSource(BUILDING_FOOTPRINT_SOURCE_ID);
          style.addSource(buildingFootprintGeoJsonSource);
          FillLayer buildingFootprintFillLayer = new FillLayer(BUILDING_FOOTPRINT_LAYER_ID,
              BUILDING_FOOTPRINT_SOURCE_ID);
          buildingFootprintFillLayer.setProperties(
              fillColor(color == null ? DEFAULT_FOOTPRINT_COLOR :
                  color),
              fillOpacity(opacity == null ? DEFAULT_BUILDING_FOOTPRINT_OPACITY :
                  opacity)
          );
          buildingFootprintFillLayer.setFilter(eq(get("id"), "47"));
          style.addLayerAbove(buildingFootprintFillLayer, BUILDING_LAYER_ID);
        }
      }
    });
  }

  /**
   * Customize and add a {@link FillExtrusionLayer} to the map to show a
   * highlighted building extrusion.
   */
  private void addHighlightExtrusionLayerToMap() {
    mapboxMap.getStyle(new Style.OnStyleLoaded() {
      @Override
      public void onStyleLoaded(@NonNull Style style) {
        VectorSource buildingFootprintVectorSource = new VectorSource(BUILDING_VECTOR_SOURCE_ID, "mapbox://mapbox.mapbox-streets-v8");
        style.addSource(buildingFootprintVectorSource);
        FillExtrusionLayer buildingFillExtrusionLayer = new FillExtrusionLayer(BUILDING_HIGHLIGHTED_EXTRUSION_LAYER_ID,
            BUILDING_VECTOR_SOURCE_ID);
        buildingFillExtrusionLayer.setSourceLayer("building");
        buildingFillExtrusionLayer.setProperties(
            fillExtrusionColor(color == null ? DEFAULT_FOOTPRINT_COLOR :
                color),
            fillExtrusionOpacity(opacity == null ? DEFAULT_BUILDING_FOOTPRINT_OPACITY :
                opacity),
            fillExtrusionHeight(get("height"))
        );
        if (style.getLayerAs(BUILDING_FOOTPRINT_LAYER_ID) != null) {
          style.addLayerAbove(buildingFillExtrusionLayer, BUILDING_FOOTPRINT_LAYER_ID);
        } else if (style.getLayerAs(BUILDING_LAYER_ID) != null) {
          style.addLayer(buildingFillExtrusionLayer);
        }
      }
    });
  }
}
