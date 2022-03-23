package de.danoeh.antennapod.core.storage;

import android.app.backup.BackupManager;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;

import de.danoeh.antennapod.core.R;
import de.danoeh.antennapod.core.event.DownloadLogEvent;
import de.danoeh.antennapod.core.preferences.PlaybackPreferences;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.service.download.DownloadService;
import de.danoeh.antennapod.core.service.playback.PlaybackService;
import de.danoeh.antennapod.core.sync.queue.SynchronizationQueueSink;
import de.danoeh.antennapod.core.util.FeedItemPermutors;
import de.danoeh.antennapod.core.util.IntentUtils;
import de.danoeh.antennapod.core.util.LongList;
import de.danoeh.antennapod.core.util.Permutor;
import de.danoeh.antennapod.core.util.playback.PlayableUtils;
import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.event.QueueEvent;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.net.sync.model.EpisodeAction;

public class FeedMediaHelper {
    private String TAG = "";

    public FeedMediaHelper(String tag) {
        this.TAG = tag;
    }

    public boolean deleteFeedMediaSynchronous(
            @NonNull Context context, @NonNull FeedMedia media) {
        Log.i(TAG, String.format(Locale.US, "Requested to delete FeedMedia [id=%d, title=%s, downloaded=%s",
                media.getId(), media.getEpisodeTitle(), media.isDownloaded()));
        if (media.isDownloaded()) {
            // delete downloaded media file
            File mediaFile = new File(media.getFile_url());
            if (mediaFile.exists() && !mediaFile.delete()) {
                MessageEvent evt = new MessageEvent(context.getString(R.string.delete_failed));
                EventBus.getDefault().post(evt);
                return false;
            }
            media.setDownloaded(false);
            media.setFile_url(null);
            media.setHasEmbeddedPicture(false);
            PodDBAdapter adapter = PodDBAdapter.getInstance();
            adapter.open();
            adapter.setMedia(media);
            adapter.close();

            if (media.getId() == PlaybackPreferences.getCurrentlyPlayingFeedMediaId()) {
                PlaybackPreferences.writeNoMediaPlaying();
                IntentUtils.sendLocalBroadcast(context, PlaybackService.ACTION_SHUTDOWN_PLAYBACK_SERVICE);

                NotificationManagerCompat nm = NotificationManagerCompat.from(context);
                nm.cancel(R.id.notification_playing);
            }

            // Gpodder: queue delete action for synchronization
            FeedItem item = media.getItem();
            EpisodeAction action = new EpisodeAction.Builder(item, EpisodeAction.DELETE)
                    .currentTimestamp()
                    .build();
            SynchronizationQueueSink.enqueueEpisodeActionIfSynchronizationIsActive(context, action);
        }
        EventBus.getDefault().post(FeedItemEvent.deletedMedia(Collections.singletonList(media.getItem())));
        return true;
    }

    public void deleteFeedItemsSynchronous(@NonNull Context context, @NonNull List<FeedItem> items) {
        List<FeedItem> queue = DBReader.getQueue();
        List<FeedItem> removedFromQueue = new ArrayList<>();
        for (FeedItem item : items) {
            if (queue.remove(item)) {
                removedFromQueue.add(item);
            }
            if (item.getMedia() != null) {
                if (item.getMedia().getId() == PlaybackPreferences.getCurrentlyPlayingFeedMediaId()) {
                    // Applies to both downloaded and streamed media
                    PlaybackPreferences.writeNoMediaPlaying();
                    IntentUtils.sendLocalBroadcast(context, PlaybackService.ACTION_SHUTDOWN_PLAYBACK_SERVICE);
                }
                if (item.getMedia().isDownloaded()) {
                    deleteFeedMediaSynchronous(context, item.getMedia());
                }
                DownloadService.cancel(context, item.getMedia().getDownload_url());
            }
        }

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        if (!removedFromQueue.isEmpty()) {
            adapter.setQueue(queue);
        }
        adapter.removeFeedItems(items);
        adapter.close();

        for (FeedItem item : removedFromQueue) {
            EventBus.getDefault().post(QueueEvent.irreversibleRemoved(item));
        }

        // we assume we also removed download log entries for the feed or its media files.
        // especially important if download or refresh failed, as the user should not be able
        // to retry these
        EventBus.getDefault().post(DownloadLogEvent.listUpdated());

        BackupManager backupManager = new BackupManager(context);
        backupManager.dataChanged();
    }

    public void addQueueItemAt(final Context context, final long itemId,
                                           final int index, final boolean performAutoDownload) {
        final PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        final List<FeedItem> queue = DBReader.getQueue(adapter);
        FeedItem item;

        if (queue != null) {
            if (!itemListContains(queue, itemId)) {
                item = DBReader.getFeedItem(itemId);
                if (item != null) {
                    queue.add(index, item);
                    adapter.setQueue(queue);
                    item.addTag(FeedItem.TAG_QUEUE);
                    EventBus.getDefault().post(QueueEvent.added(item, index));
                    EventBus.getDefault().post(FeedItemEvent.updated(item));
                    if (item.isNew()) {
                        DBWriter.markItemPlayed(FeedItem.UNPLAYED, item.getId());
                    }
                }
            }
        }

        adapter.close();
        if (performAutoDownload) {
            DBTasks.autodownloadUndownloadedItems(context);
        }
    }

        public void addQueueItem(final Context context, final boolean performAutoDownload,
                             final boolean markAsUnplayed, final long... itemIds) {
        if (itemIds.length < 1) {
            return;
        }

        final PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        final List<FeedItem> queue = DBReader.getQueue(adapter);

        boolean queueModified = false;
        LongList markAsUnplayedIds = new LongList();
        List<QueueEvent> events = new ArrayList<>();
        List<FeedItem> updatedItems = new ArrayList<>();
        ItemEnqueuePositionCalculator positionCalculator =
                new ItemEnqueuePositionCalculator(UserPreferences.getEnqueueLocation());
        Playable currentlyPlaying = PlayableUtils.createInstanceFromPreferences(context);
        int insertPosition = positionCalculator.calcPosition(queue, currentlyPlaying);
        for (long itemId : itemIds) {
            if (!itemListContains(queue, itemId)) {
                final FeedItem item = DBReader.getFeedItem(itemId);
                if (item != null) {
                    queue.add(insertPosition, item);
                    events.add(QueueEvent.added(item, insertPosition));

                    item.addTag(FeedItem.TAG_QUEUE);
                    updatedItems.add(item);
                    queueModified = true;
                    if (item.isNew()) {
                        markAsUnplayedIds.add(item.getId());
                    }
                    insertPosition++;
                }
            }
        }
        if (queueModified) {
            applySortOrder(queue, events);
            adapter.setQueue(queue);
            for (QueueEvent event : events) {
                EventBus.getDefault().post(event);
            }
            EventBus.getDefault().post(FeedItemEvent.updated(updatedItems));
            if (markAsUnplayed && markAsUnplayedIds.size() > 0) {
                DBWriter.markItemPlayed(FeedItem.UNPLAYED, markAsUnplayedIds.toArray());
            }
        }
        adapter.close();
        if (performAutoDownload) {
            DBTasks.autodownloadUndownloadedItems(context);
        }


    }

    private static boolean itemListContains(List<FeedItem> items, long itemId) {
        return indexInItemList(items, itemId) >= 0;
    }

    private static int indexInItemList(List<FeedItem> items, long itemId) {
        for (int i = 0; i < items.size(); i++) {
            FeedItem item = items.get(i);
            if (item.getId() == itemId) {
                return i;
            }
        }
        return -1;
    }

    public void applySortOrder(List<FeedItem> queue, List<QueueEvent> events) {
        if (!UserPreferences.isQueueKeepSorted()) {
            // queue is not in keep sorted mode, there's nothing to do
            return;
        }

        // Sort queue by configured sort order
        SortOrder sortOrder = UserPreferences.getQueueKeepSortedOrder();
        if (sortOrder == SortOrder.RANDOM) {
            // do not shuffle the list on every change
            return;
        }
        Permutor<FeedItem> permutor = FeedItemPermutors.getPermutor(sortOrder);
        permutor.reorder(queue);

        // Replace ADDED events by a single SORTED event
        events.clear();
        events.add(QueueEvent.sorted(queue));
    }

    public void removeQueueItemSynchronous(final Context context,
                                                   final boolean performAutoDownload,
                                                   final long... itemIds) {
        if (itemIds.length < 1) {
            return;
        }
        final PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        final List<FeedItem> queue = DBReader.getQueue(adapter);

        if (queue != null) {
            boolean queueModified = false;
            List<QueueEvent> events = new ArrayList<>();
            List<FeedItem> updatedItems = new ArrayList<>();
            for (long itemId : itemIds) {
                int position = indexInItemList(queue, itemId);
                if (position >= 0) {
                    final FeedItem item = DBReader.getFeedItem(itemId);
                    if (item == null) {
                        Log.e(TAG, "removeQueueItem - item in queue but somehow cannot be loaded." +
                                " Item ignored. It should never happen. id:" + itemId);
                        continue;
                    }
                    queue.remove(position);
                    item.removeTag(FeedItem.TAG_QUEUE);
                    events.add(QueueEvent.removed(item));
                    updatedItems.add(item);
                    queueModified = true;
                } else {
                    Log.v(TAG, "removeQueueItem - item  not in queue:" + itemId);
                }
            }
            if (queueModified) {
                adapter.setQueue(queue);
                for (QueueEvent event : events) {
                    EventBus.getDefault().post(event);
                }
                EventBus.getDefault().post(FeedItemEvent.updated(updatedItems));
            } else {
                Log.w(TAG, "Queue was not modified by call to removeQueueItem");
            }
        } else {
            Log.e(TAG, "removeQueueItem: Could not load queue");
        }
        adapter.close();
        if (performAutoDownload) {
            DBTasks.autodownloadUndownloadedItems(context);
        }
    }

}
