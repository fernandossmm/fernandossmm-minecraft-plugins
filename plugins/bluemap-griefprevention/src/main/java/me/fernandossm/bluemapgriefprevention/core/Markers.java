package me.fernandossm.bluemapgriefprevention.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns claims into markers: per world and owner (each player, and all admin claims together), every set
 * of claims connected along an edge becomes one shape, holes included.
 */
public final class Markers {

    private static final String ADMIN = "admin";

    private Markers() {}

    /** World name → marker id → marker. Ids are stable while the claims behind a marker do not change. */
    public static Map<String, Map<String, MarkerSpec>> of(List<ClaimView> claims, Settings settings) {
        Map<String, Map<String, List<ClaimView>>> byWorldAndOwner = new LinkedHashMap<>();
        claims.stream()
                .filter(c -> settings.manages(c.world()))
                .filter(c -> c.admin() ? settings.adminClaims() : settings.playerClaims() && c.owner() != null)
                .sorted(Comparator.comparingLong(ClaimView::id))
                .forEach(c -> byWorldAndOwner
                        .computeIfAbsent(c.world(), w -> new LinkedHashMap<>())
                        .computeIfAbsent(ownerKey(c), o -> new ArrayList<>())
                        .add(c));

        Map<String, Map<String, MarkerSpec>> result = new LinkedHashMap<>();
        byWorldAndOwner.forEach((world, byOwner) -> {
            Map<String, MarkerSpec> markers = new LinkedHashMap<>();
            byOwner.forEach((owner, owned) -> {
                List<Rect> rects = owned.stream().map(ClaimView::area).toList();
                for (List<Integer> group : RectUnion.groups(rects)) {
                    List<ClaimView> members = group.stream().map(owned::get).toList();
                    List<Outline> outlines = RectUnion.outlines(group.stream().map(rects::get).toList());
                    String id = "claim-" + owner + "-" + members.getFirst().id();
                    for (int i = 0; i < outlines.size(); i++) {
                        markers.put(i == 0 ? id : id + "-" + i, spec(members, outlines.get(i), settings));
                    }
                }
            });
            result.put(world, markers);
        });
        return result;
    }

    private static String ownerKey(ClaimView c) {
        return c.admin() ? ADMIN : c.owner().toString();
    }

    private static MarkerSpec spec(List<ClaimView> claims, Outline outline, Settings settings) {
        ClaimView first = claims.getFirst();
        boolean admin = first.admin();
        String name = first.ownerName() == null || first.ownerName().isBlank() ? "unknown" : first.ownerName();
        String title = admin ? "Admin claim" : settings.owner() == Settings.OwnerShown.NONE ? "Claim" : "Claim of " + name;
        String label = admin ? "Admin claim" : settings.owner() == Settings.OwnerShown.LIST ? title : "Claim";
        long area = claims.stream().mapToLong(c -> c.area().area()).sum();
        String size = claims.size() == 1
                ? String.format(Locale.ROOT, "%,d blocks", area)
                : String.format(Locale.ROOT, "%d claims · %,d blocks", claims.size(), area);
        String detail = "<b>" + escape(title) + "</b><br>" + escape(size);
        int y = claims.stream().mapToInt(ClaimView::surfaceY).max().orElse(64);
        int color = admin ? settings.adminColor() : LocatorColor.of(first.owner());
        return new MarkerSpec(label, detail, outline, y, color, settings.lineOpacity(), settings.fillOpacity(), settings.lineWidth());
    }

    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char ch : text.toCharArray()) {
            switch (ch) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(ch);
            }
        }
        return out.toString();
    }
}
