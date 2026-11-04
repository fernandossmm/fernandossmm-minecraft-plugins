package me.fernandossm.bluemapgriefprevention.paper;

import java.util.Map;
import me.fernandossm.bluemapgriefprevention.core.MarkerSpec;
import me.fernandossm.bluemapgriefprevention.core.Settings;

/** Where markers are drawn. An interface so the sync can be tested without BlueMap. */
public interface MapSink {

    /** The map is up and can take markers. */
    boolean ready();

    /** Makes the maps show exactly these markers (world name → marker id → marker), and nothing else of ours. */
    void show(Map<String, Map<String, MarkerSpec>> markersByWorld, Settings settings);
}
