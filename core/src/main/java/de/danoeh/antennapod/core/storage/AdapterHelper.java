package de.danoeh.antennapod.core.storage;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.util.List;
import java.util.concurrent.Future;

import de.danoeh.antennapod.core.util.Permutor;
import de.danoeh.antennapod.event.FavoritesEvent;
import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.event.QueueEvent;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;

public class AdapterHelper {
    public void addFavoriteItem(final FeedItem item) {
        final PodDBAdapter adapter = PodDBAdapter.getInstance().open();
        adapter.addFavoriteItem(item);
        adapter.close();
        item.addTag(FeedItem.TAG_FAVORITE);
        EventBus.getDefault().post(FavoritesEvent.added(item));
        EventBus.getDefault().post(FeedItemEvent.updated(item));
    }

    public void removeFavoriteItem(final FeedItem item) {
        final PodDBAdapter adapter = PodDBAdapter.getInstance().open();
        adapter.removeFavoriteItem(item);
        adapter.close();
        item.removeTag(FeedItem.TAG_FAVORITE);
        EventBus.getDefault().post(FavoritesEvent.removed(item));
        EventBus.getDefault().post(FeedItemEvent.updated(item));
    }

    public void reorderQueue(Permutor<FeedItem> permutor, final boolean broadcastUpdate) {
        final PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        final List<FeedItem> queue = DBReader.getQueue(adapter);

        if (queue != null) {
            permutor.reorder(queue);
            adapter.setQueue(queue);
            if (broadcastUpdate) {
                EventBus.getDefault().post(QueueEvent.sorted(queue));
            }
        } else {
            Log.e("", "reorderQueue: Could not load queue");
        }
        adapter.close();

    }

    public void setFeedMedia(final FeedMedia media) {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setMedia(media);
        adapter.close();
    }

    public void clearQueue() {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.clearQueue();
        adapter.close();

        EventBus.getDefault().post(QueueEvent.cleared());
    }


}
