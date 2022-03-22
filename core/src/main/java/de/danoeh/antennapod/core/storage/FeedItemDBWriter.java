package de.danoeh.antennapod.core.storage;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

import de.danoeh.antennapod.core.feed.FeedEvent;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.util.FeedItemPermutors;
import de.danoeh.antennapod.core.util.LongList;
import de.danoeh.antennapod.core.util.Permutor;
import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.event.UnreadItemsUpdateEvent;
import de.danoeh.antennapod.event.playback.PlaybackHistoryEvent;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.SortOrder;

public class FeedItemDBWriter extends DBWriter {
    /**
     * Deletes a downloaded FeedMedia file from the storage device.
     *
     * @param context A context that is used for opening a database connection.
     * @param mediaId ID of the FeedMedia object whose downloaded file should be deleted.
     */
    public static Future<?> deleteFeedMediaOfItem(@NonNull final Context context,
                                                  final long mediaId) {
        return dbExec.submit(() -> {
            final FeedMedia media = DBReader.getFeedMedia(mediaId);
            if (media != null) {
                boolean result = deleteFeedMediaSynchronous(context, media);

                if (result && UserPreferences.shouldDeleteRemoveFromQueue()) {
                    removeQueueItemSynchronous(context, false, media.getItem().getId());
                }
            }
        });
    }

    /**
     * Adds a FeedMedia object to the playback history. A FeedMedia object is in the playback history if
     * its playback completion date is set to a non-null value. This method will set the playback completion date to the
     * current date regardless of the current value.
     *
     * @param media FeedMedia that should be added to the playback history.
     */
    public static Future<?> addItemToPlaybackHistory(final FeedMedia media) {
        return dbExec.submit(() -> {
            Log.d("TAG", "Adding new item to playback history");
            media.setPlaybackCompletionDate(new Date());

            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedMediaPlaybackCompletionDate(media);
            adapter.close();
            EventBus.getDefault().post(PlaybackHistoryEvent.listUpdated());

        });
    }

    /**
     * Inserts a FeedItem in the queue at the specified index. The 'read'-attribute of the FeedItem will be set to
     * true. If the FeedItem is already in the queue, the queue will not be modified.
     *
     * @param context             A context that is used for opening a database connection.
     * @param itemId              ID of the FeedItem that should be added to the queue.
     * @param index               Destination index. Must be in range 0..queue.size()
     * @param performAutoDownload True if an auto-download process should be started after the operation
     * @throws IndexOutOfBoundsException if index < 0 || index >= queue.size()
     */
    public static Future<?> addQueueItemAt(final Context context, final long itemId,
                                           final int index, final boolean performAutoDownload) {
        return dbExec.submit(() -> {
            feedHelper.addQueueItemAt(context, itemId, index, performAutoDownload);
        });

    }

    /**
     * Appends FeedItem objects to the end of the queue. The 'read'-attribute of all items will be set to true.
     * If a FeedItem is already in the queue, the FeedItem will not change its position in the queue.
     *
     * @param context             A context that is used for opening a database connection.
     * @param performAutoDownload true if an auto-download process should be started after the operation.
     * @param itemIds             IDs of the FeedItem objects that should be added to the queue.
     */
    public static Future<?> addQueueItem(final Context context, final boolean performAutoDownload,
                                         final long... itemIds) {
        return addQueueItem(context, performAutoDownload, true, itemIds);
    }

    public static Future<?> addQueueItem(final Context context, final FeedItem... items) {
        return addQueueItem(context, true, items);
    }

    public static Future<?> addQueueItem(final Context context, boolean markAsUnplayed, final FeedItem... items) {
        LongList itemIds = new LongList(items.length);
        for (FeedItem item : items) {
            itemIds.add(item.getId());
            item.addTag(FeedItem.TAG_QUEUE);
        }
        return addQueueItem(context, false, markAsUnplayed, itemIds.toArray());
    }

    /**
     * Appends FeedItem objects to the end of the queue. The 'read'-attribute of all items will be set to true.
     * If a FeedItem is already in the queue, the FeedItem will not change its position in the queue.
     *
     * @param context             A context that is used for opening a database connection.
     * @param performAutoDownload true if an auto-download process should be started after the operation.
     * @param markAsUnplayed      true if the items should be marked as unplayed when enqueueing
     * @param itemIds             IDs of the FeedItem objects that should be added to the queue.
     */
    public static Future<?> addQueueItem(final Context context, final boolean performAutoDownload,
                                         final boolean markAsUnplayed, final long... itemIds) {
        return dbExec.submit(() -> {
            feedHelper.addQueueItem(context, performAutoDownload, markAsUnplayed, itemIds);
        });
    }

    /**
     * Removes a FeedItem object from the queue.
     *
     * @param context             A context that is used for opening a database connection.
     * @param performAutoDownload true if an auto-download process should be started after the operation.
     * @param item                FeedItem that should be removed.
     */
    public static Future<?> removeQueueItem(final Context context,
                                            final boolean performAutoDownload, final FeedItem item) {
        return dbExec.submit(() -> removeQueueItemSynchronous(context, performAutoDownload, item.getId()));
    }

    public static Future<?> removeQueueItem(final Context context, final boolean performAutoDownload,
                                            final long... itemIds) {
        return dbExec.submit(() -> removeQueueItemSynchronous(context, performAutoDownload, itemIds));
    }

    protected static void removeQueueItemSynchronous(final Context context,
                                                     final boolean performAutoDownload,
                                                     final long... itemIds) {
        feedHelper.removeQueueItemSynchronous(context, performAutoDownload, itemIds);
    }

    /**
     * Sets the 'read'-attribute of a FeedItem to the specified value.
     *
     * @param item               The FeedItem object
     * @param played             New value of the 'read'-attribute one of FeedItem.PLAYED,
     *                           FeedItem.NEW, FeedItem.UNPLAYED
     * @param resetMediaPosition true if this method should also reset the position of the FeedItem's FeedMedia object.
     */
    @NonNull
    public static Future<?> markItemPlayed(FeedItem item, int played, boolean resetMediaPosition) {
        long mediaId = (item.hasMedia()) ? item.getMedia().getId() : 0;
        return markItemPlayed(item.getId(), played, mediaId, resetMediaPosition);
    }

    @NonNull
    private static Future<?> markItemPlayed(final long itemId,
                                            final int played,
                                            final long mediaId,
                                            final boolean resetMediaPosition) {
        return dbExec.submit(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItemRead(played, itemId, mediaId,
                    resetMediaPosition);
            adapter.close();

            EventBus.getDefault().post(new UnreadItemsUpdateEvent());
        });
    }

    /**
     * Sets the 'read'-attribute of all FeedItems to PLAYED.
     */
    public static Future<?> markAllItemsRead() {
        return dbExec.submit(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItems(FeedItem.PLAYED);
            adapter.close();

            EventBus.getDefault().post(new UnreadItemsUpdateEvent());
        });
    }

    /**
     * Sets the 'read'-attribute of all NEW FeedItems to UNPLAYED.
     */
    public static Future<?> removeAllNewFlags() {
        return dbExec.submit(() -> {
            final PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItems(FeedItem.NEW, FeedItem.UNPLAYED);
            adapter.close();

            EventBus.getDefault().post(new UnreadItemsUpdateEvent());
        });
    }


    /**
     * Saves a FeedItem object in the database. This method will save all attributes of the FeedItem object including
     * the content of FeedComponent-attributes.
     *
     * @param item The FeedItem object.
     */
    public static Future<?> setFeedItem(final FeedItem item) {
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setSingleFeedItem(item);
            adapter.close();
            EventBus.getDefault().post(FeedItemEvent.updated(item));
        });
    }

    /**
     * Sort the FeedItems in the queue with the given the named sort order.
     *
     * @param broadcastUpdate <code>true</code> if this operation should trigger a
     *                        QueueUpdateBroadcast. This option should be set to <code>false</code>
     *                        if the caller wants to avoid unexpected updates of the GUI.
     */
    public static Future<?> reorderQueue(@Nullable SortOrder sortOrder, final boolean broadcastUpdate) {
        if (sortOrder == null) {
            Log.w("TAG", "reorderQueue() - sortOrder is null. Do nothing.");
            return dbExec.submit(() -> { });
        }
        final Permutor<FeedItem> permutor = FeedItemPermutors.getPermutor(sortOrder);
        return dbExec.submit(() -> {
            adapterHelper.reorderQueue(permutor, broadcastUpdate);
        });
    }


    /**
     * Set filter of the feed
     *
     * @param feedId       The feed's ID
     * @param filterValues Values that represent properties to filter by
     */
    public static Future<?> setFeedItemsFilter(final long feedId,
                                               final Set<String> filterValues) {
        Log.d("TAG", "setFeedItemsFilter() called with: " + "feedId = [" + feedId + "], filterValues = [" + filterValues + "]");
        return dbExec.submit(() -> {
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setFeedItemFilter(feedId, filterValues);
            adapter.close();
            EventBus.getDefault().post(new FeedEvent(FeedEvent.Action.FILTER_CHANGED, feedId));
        });
    }

    /**
     * Removes all FeedItem objects from the queue.
     */
    public static Future<?> clearQueue() {
        return dbExec.submit(() -> {
            adapterHelper.clearQueue();
        });
    }

    public static Future<?> toggleFavoriteItem(final FeedItem item) {
        if (item.isTagged(FeedItem.TAG_FAVORITE)) {
            return removeFavoriteItem(item);
        } else {
            return addFavoriteItem(item);
        }
    }

    public static Future<?> addFavoriteItem(final FeedItem item) {
        return dbExec.submit(() -> {
            adapterHelper.addFavoriteItem(item);
        });
    }

    public static Future<?> removeFavoriteItem(final FeedItem item) {
        return dbExec.submit(() -> {
            adapterHelper.removeFavoriteItem(item);
        });
    }

    /**
     * Remove the listed items and their FeedMedia entries.
     * Deleting media also removes the download log entries.
     */
    @NonNull
    public static Future<?> deleteFeedItems(@NonNull Context context, @NonNull List<FeedItem> items) {
        return dbExec.submit(() -> deleteFeedItemsSynchronous(context, items));
    }

    /**
     * Changes the position of a FeedItem in the queue.
     *
     * @param from            Source index. Must be in range 0..queue.size()-1.
     * @param to              Destination index. Must be in range 0..queue.size()-1.
     * @param broadcastUpdate true if this operation should trigger a QueueUpdateBroadcast. This option should be set to
     *                        false if the caller wants to avoid unexpected updates of the GUI.
     * @throws IndexOutOfBoundsException if (to < 0 || to >= queue.size()) || (from < 0 || from >= queue.size())
     */
    public static Future<?> moveQueueItem(final int from,
                                          final int to, final boolean broadcastUpdate) {
        return dbExec.submit(() -> moveQueueItemHelper(from, to, broadcastUpdate));
    }
}
