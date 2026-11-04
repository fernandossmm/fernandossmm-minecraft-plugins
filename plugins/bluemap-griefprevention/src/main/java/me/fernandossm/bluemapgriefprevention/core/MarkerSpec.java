package me.fernandossm.bluemapgriefprevention.core;

/**
 * Everything one map marker shows, independent of BlueMap's classes. Equal specs draw equal markers, which
 * is how an unchanged claim is left alone on a resync.
 *
 * @param label   the name in BlueMap's marker list (shape markers have no hover in BlueMap)
 * @param detail  HTML shown when the marker is clicked
 * @param y       the height the flat shape is drawn at
 * @param color   0xRRGGBB, used for the outline and the fill
 */
public record MarkerSpec(String label, String detail, Outline outline, float y, int color, float lineOpacity,
                         float fillOpacity, int lineWidth) {}
