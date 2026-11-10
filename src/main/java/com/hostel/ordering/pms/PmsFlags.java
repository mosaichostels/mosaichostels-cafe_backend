package com.hostel.ordering.pms;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * The only reader of the PMS write flags and canary lists. Callers go through here, never through
 * {@link PmsProperties}, so a later runtime-override layer applies everywhere at once.
 */
@Component
public class PmsFlags {

    private final PmsProperties props;

    public PmsFlags(PmsProperties props) {
        this.props = props;
    }

    /** A missing flag means off. */
    public boolean isWriteEnabled(String feature) {
        return Boolean.TRUE.equals(props.getWrites().get(feature));
    }

    public void requireWrite(String feature) {
        if (!isWriteEnabled(feature)) {
            throw new PmsFeatureDisabledException("PMS write feature is switched off");
        }
    }

    /** Target ids a write may touch while the feature is being proven; empty means any target. */
    public List<String> canary(String feature) {
        List<String> ids = props.getCanary().get(feature);
        return ids == null ? List.of() : ids;
    }

    /** Every feature that has a write flag or a canary list configured, sorted; for the status view. */
    public Set<String> configuredFeatures() {
        Set<String> features = new TreeSet<>(props.getWrites().keySet());
        features.addAll(props.getCanary().keySet());
        return features;
    }
}
