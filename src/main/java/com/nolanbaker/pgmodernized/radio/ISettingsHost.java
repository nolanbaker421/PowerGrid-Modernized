package com.nolanbaker.pgmodernized.radio;

import java.util.List;
import java.util.Map;

/**
 * A block entity with a few typed settings: a link ID, an aim in degrees. The client reads the
 * fields off its synced copy to draw the screen and sends the edited values back in one packet.
 */
public interface ISettingsHost {
    /** One field: its key in the packet, the lang key of its label, whether it takes a number, and its present value. */
    record Field(String key, String label, boolean numeric, String value) {}

    List<Field> settingsFields();

    /** Server side: the edited values, keyed as {@link #settingsFields()} named them. */
    void applySettings(Map<String, String> values);
}
