package de.danoeh.antennapod.core.storage;

import java.util.concurrent.Future;

import de.danoeh.antennapod.model.feed.FeedMedia;

public class FeedMediaDBWriter extends DBWriter {

    /**
     * Saves a FeedMedia object in the database. This method will save all attributes of the FeedMedia object. The
     * contents of FeedComponent-attributes (e.g. the FeedMedia's 'item'-attribute) will not be saved.
     *
     * @param media The FeedMedia object.
     */
    public static Future<?> setFeedMedia(final FeedMedia media) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setMedia(media);
            adapter.close();
        });
    }

    /**
     * Saves the 'position', 'duration' and 'last played time' attributes of a FeedMedia object
     *
     * @param media The FeedMedia object.
     */
    public static Future<?> setFeedMediaPlaybackInformation(final FeedMedia media) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedMediaPlaybackInformation(media);
            adapter.close();
        });
    }
}
