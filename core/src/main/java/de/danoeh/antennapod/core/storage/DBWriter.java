package de.danoeh.antennapod.core.storage;

import android.app.backup.BackupManager;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import de.danoeh.antennapod.core.service.download.DownloadService;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import de.danoeh.antennapod.core.R;
import de.danoeh.antennapod.core.event.DownloadLogEvent;
import de.danoeh.antennapod.event.FavoritesEvent;
import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.event.FeedListUpdateEvent;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.event.playback.PlaybackHistoryEvent;
import de.danoeh.antennapod.event.QueueEvent;
import de.danoeh.antennapod.event.UnreadItemsUpdateEvent;
import de.danoeh.antennapod.core.feed.FeedEvent;
import de.danoeh.antennapod.core.preferences.PlaybackPreferences;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.model.download.DownloadStatus;
import de.danoeh.antennapod.core.service.playback.PlaybackService;
import de.danoeh.antennapod.core.sync.queue.SynchronizationQueueSink;
import de.danoeh.antennapod.core.util.FeedItemPermutors;
import de.danoeh.antennapod.core.util.IntentUtils;
import de.danoeh.antennapod.core.util.LongList;
import de.danoeh.antennapod.core.util.Permutor;
import de.danoeh.antennapod.core.util.playback.PlayableUtils;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.net.sync.model.EpisodeAction;

/**
 * Provides methods for writing data to AntennaPod's database.
 * In general, DBWriter-methods will be executed on an internal ExecutorService.
 * Some methods return a Future-object which the caller can use for waiting for the method's completion. The returned Future's
 * will NOT contain any results.
 */
public abstract class DBWriter {

    private static final String TAG = "DBWriter";

    protected static final ExecutorService dbExec;

    protected static final FeedMediaHelper feedHelper = new FeedMediaHelper(TAG);
    protected static final AdapterHelper adapterHelper = new AdapterHelper();

    static {
        dbExec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("DatabaseExecutor");
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
    }

    public DBWriter() {
    }

    /**
     * Wait until all threads are finished to avoid the "Illegal connection pointer" error of
     * Robolectric. Call this method only for unit tests.
     */
    public static void tearDownTests() {
        try {
            dbExec.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // ignore error
        }
    }

    protected static boolean deleteFeedMediaSynchronous(
            @NonNull Context context, @NonNull FeedMedia media) {
        return feedHelper.deleteFeedMediaSynchronous(context, media);
    }


    /**
     * Remove the listed items and their FeedMedia entries.
     * Deleting media also removes the download log entries.
     */
    protected static void deleteFeedItemsSynchronous(@NonNull Context context, @NonNull List<FeedItem> items) {
        feedHelper.deleteFeedItemsSynchronous(context, items);
    }

    /**
     * Deletes the entire playback history.
     */
    public static Future<?> clearPlaybackHistory() {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.clearPlaybackHistory();
            adapter.close();
            EventBus.getDefault().post(PlaybackHistoryEvent.listUpdated());
        });
    }

    /**
     * Deletes the entire download log.
     */
    public static Future<?> clearDownloadLog() {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.clearDownloadLog();
            adapter.close();
            EventBus.getDefault().post(DownloadLogEvent.listUpdated());
        });
    }


    /**
     * Adds a Download status object to the download log.
     *
     * @param status The DownloadStatus object.
     */
    public static Future<?> addDownloadStatus(final DownloadStatus status) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setDownloadStatus(status);
            adapter.close();
            EventBus.getDefault().post(DownloadLogEvent.listUpdated());
        });

    }

    /**
     * Moves the specified item to the top of the queue.
     *
     * @param itemId          The item to move to the top of the queue
     * @param broadcastUpdate true if this operation should trigger a QueueUpdateBroadcast. This option should be set to
     */
    public static Future<?> moveQueueItemToTop(final long itemId, final boolean broadcastUpdate) {
        return dbExec.submit(() -> {
            LongList queueIdList = DBReader.getQueueIDList();
            int index = queueIdList.indexOf(itemId);
            if (index >= 0) {
                moveQueueItemHelper(index, 0, broadcastUpdate);
            } else {
                Log.e(TAG, "moveQueueItemToTop: item not found");
            }
        });
    }

    /**
     * Moves the specified item to the bottom of the queue.
     *
     * @param itemId          The item to move to the bottom of the queue
     * @param broadcastUpdate true if this operation should trigger a QueueUpdateBroadcast. This option should be set to
     */
    public static Future<?> moveQueueItemToBottom(final long itemId,
                                                  final boolean broadcastUpdate) {
        return dbExec.submit(() -> {
            LongList queueIdList = DBReader.getQueueIDList();
            int index = queueIdList.indexOf(itemId);
            if (index >= 0) {
                moveQueueItemHelper(index, queueIdList.size() - 1,
                        broadcastUpdate);
            } else {
                Log.e(TAG, "moveQueueItemToBottom: item not found");
            }
        });
    }

    /**
     * Changes the position of a FeedItem in the queue.
     * <p/>
     * This function must be run using the ExecutorService (dbExec).
     *
     * @param from            Source index. Must be in range 0..queue.size()-1.
     * @param to              Destination index. Must be in range 0..queue.size()-1.
     * @param broadcastUpdate true if this operation should trigger a QueueUpdateBroadcast. This option should be set to
     *                        false if the caller wants to avoid unexpected updates of the GUI.
     * @throws IndexOutOfBoundsException if (to < 0 || to >= queue.size()) || (from < 0 || from >= queue.size())
     */
    protected static void moveQueueItemHelper(final int from,
                                            final int to, final boolean broadcastUpdate) {
        final PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        final List<FeedItem> queue = DBReader.getQueue(adapter);

        if (queue != null) {
            if (from >= 0 && from < queue.size() && to >= 0 && to < queue.size()) {
                final FeedItem item = queue.remove(from);
                queue.add(to, item);

                adapter.setQueue(queue);
                if (broadcastUpdate) {
                    EventBus.getDefault().post(QueueEvent.moved(item, to));
                }
            }
        } else {
            Log.e(TAG, "moveQueueItemHelper: Could not load queue");
        }
        adapter.close();
    }

    /*
     * Sets the 'read'-attribute of all specified FeedItems
     *
     * @param played  New value of the 'read'-attribute, one of FeedItem.PLAYED, FeedItem.NEW,
     *                FeedItem.UNPLAYED
     * @param itemIds IDs of the FeedItems.
     */
    public static Future<?> markItemPlayed(final int played, final long... itemIds) {
        return markItemPlayed(played, true, itemIds);
    }

    /*
     * Sets the 'read'-attribute of all specified FeedItems
     *
     * @param played  New value of the 'read'-attribute, one of FeedItem.PLAYED, FeedItem.NEW,
     *                FeedItem.UNPLAYED
     * @param broadcastUpdate true if this operation should trigger a UnreadItemsUpdate broadcast.
     *        This option is usually set to true
     * @param itemIds IDs of the FeedItems.
     */
    public static Future<?> markItemPlayed(final int played, final boolean broadcastUpdate,
                                           final long... itemIds) {
        return dbExec.submit(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItemRead(played, itemIds);
            adapter.close();
            if (broadcastUpdate) {
                EventBus.getDefault().post(new UnreadItemsUpdateEvent());
            }
        });
    }

    public static Future<?> setItemList(final List<FeedItem> items) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.storeFeedItemlist(items);
            adapter.close();
            EventBus.getDefault().post(FeedItemEvent.updated(items));
        });
    }

    /**
     * Saves if a feed's last update failed
     *
     * @param lastUpdateFailed true if last update failed
     */
    public static Future<?> setFeedLastUpdateFailed(final long feedId,
                                                    final boolean lastUpdateFailed) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedLastUpdateFailed(feedId, lastUpdateFailed);
            adapter.close();
            EventBus.getDefault().post(new FeedListUpdateEvent(feedId));
        });
    }

    public static Future<?> setFeedCustomTitle(Feed feed) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedCustomTitle(feed.getId(), feed.getCustomTitle());
            adapter.close();
            EventBus.getDefault().post(new FeedListUpdateEvent(feed));
        });
    }

    /**
     * Reset the statistics in DB
     */
    @NonNull
    public static Future<?> resetStatistics() {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.resetAllMediaPlayedDuration();
            adapter.close();
        });
    }
}
