package me.fernandossm.bluemapgriefprevention.paper;

import java.util.List;
import me.fernandossm.bluemapgriefprevention.core.ClaimView;

/** Every top-level claim, as it is now. An interface so the sync can be tested without GriefPrevention. */
@FunctionalInterface
public interface ClaimSource {

    List<ClaimView> claims();
}
