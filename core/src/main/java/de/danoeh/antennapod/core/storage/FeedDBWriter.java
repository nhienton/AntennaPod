package de.danoeh.antennapod.core.storage;

import android.app.backup.BackupManager;
import android.content.Context;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.util.concurrent.Future;

import de.danoeh.antennapod.core.sync.queue.SynchronizationQueueSink;
import de.danoeh.antennapod.event.FeedListUpdateEvent;
import de.danoeh.antennapod.event.UnreadItemsUpdateEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedPreferences;

public class FeedDBWriter extends DBWriter {
    /**
     * Deletes a Feed and all downloaded files of its components like images and downloaded episodes.
     *
     * @param context A context that is used for opening a database connection.
     * @param feedId  ID of the Feed that should be deleted.
     */
    public static Future<?> deleteFeed(final Context context, final long feedId) {
        return dbExec.submit(() -> {
            final Feed feed = DBReader.getFeed(feedId);
            if (feed == null) {
                return;
            }

            // delete stored media files and mark them as read
            if (feed.getItems() == null) {
                DBReader.getFeedItemList(feed);
            }
            deleteFeedItemsSynchronous(context, feed.getItems());

            // delete feed
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.removeFeed(feed);
            adapter.close();

            if (!feed.isLocalFeed()) {
                SynchronizationQueueSink.enqueueFeedRemovedIfSynchronizationIsActive(context, feed.getDownload_url());
            }
            EventBus.getDefault().post(new FeedListUpdateEvent(feed));
        });
    }


    /**
     * Sets the 'read'-attribute of all NEW FeedItems of a specific Feed to UNPLAYED.
     *
     * @param feedId ID of the Feed.
     */
    public static Future<?> removeFeedNewFlag(final long feedId) {
        return dbExec.submit(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItems(FeedItem.NEW, FeedItem.UNPLAYED, feedId);
            adapter.close();

            EventBus.getDefault().post(new UnreadItemsUpdateEvent());
        });
    }


    /**
     * Sets the 'read'-attribute of all FeedItems of a specific Feed to PLAYED.
     *
     * @param feedId ID of the Feed.
     */
    public static Future<?> markFeedRead(final long feedId) {
        return dbExec.submit(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItems(FeedItem.PLAYED, feedId);
            adapter.close();

            EventBus.getDefault().post(new UnreadItemsUpdateEvent());
        });
    }


    static Future<?> addNewFeed(final Context context, final Feed... feeds) {
        return dbExec.submit(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setCompleteFeed(feeds);
            adapter.close();

            for (Feed feed : feeds) {
                if (!feed.isLocalFeed()) {
                    SynchronizationQueueSink.enqueueFeedAddedIfSynchronizationIsActive(context, feed.getDownload_url());
                }
            }

            BackupManager backupManager = new BackupManager(context);
            backupManager.dataChanged();
        });
    }


    static Future<?> setCompleteFeed(final Feed... feeds) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setCompleteFeed(feeds);
            adapter.close();
        });
    }


    /**
     * Updates download URL of a feed
     */
    public static Future<?> updateFeedDownloadURL(final String original, final String updated) {
        Log.d(TAG, "updateFeedDownloadURL(original: " + original + ", updated: " + updated + ")");
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedDownloadUrl(original, updated);
            adapter.close();
        });
    }


    /**
     * Saves a FeedPreferences object in the database. The Feed ID of the FeedPreferences-object MUST NOT be 0.
     *
     * @param preferences The FeedPreferences object.
     */
    public static Future<?> setFeedPreferences(final FeedPreferences preferences) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedPreferences(preferences);
            adapter.close();
            EventBus.getDefault().post(new FeedListUpdateEvent(preferences.getFeedID()));
        });
    }

}
