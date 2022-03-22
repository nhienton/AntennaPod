package de.danoeh.antennapod.model.feed;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Data Object for a whole feed.
 *
 * @author daniel
 */
public class Feed extends FeedFile {

    public static final int FEEDFILETYPE_FEED = 0;
    public static final String TYPE_RSS2 = "rss";
    public static final String TYPE_ATOM1 = "atom";
    public static final String PREFIX_LOCAL_FOLDER = "antennapod_local:";
    public static final String PREFIX_GENERATIVE_COVER = "antennapod_generative_cover:";

    private FeedDetails feedDetails;

    private List<FeedItem> items;

    private ArrayList<FeedFunding> fundingList;

    /**
     * Feed preferences.
     */

    /**
     * The page number that this feed is on. Only feeds with page number "0" should be stored in the
     * database, feed objects with a higher page number only exist temporarily and should be merged
     * into feeds with page number "0".
     * <p/>
     * This attribute's value is not saved in the database
     */
    private int pageNr;

    /**
     * True if this is a "paged feed", i.e. there exist other feed files that belong to the same
     * logical feed.
     */
    private boolean paged;

    /**
     * Link to the next page of this feed. If this feed object represents a logical feed (i.e. a feed
     * that is saved in the database) this might be null while still being a paged feed.
     */
    private String nextPageLink;

    private boolean lastUpdateFailed;

    /**
     * Contains property strings. If such a property applies to a feed item, it is not shown in the feed list
     */
    private FeedItemFilter itemfilter;

    /**
     * User-preferred sortOrder for display.
     * Only those of scope {@link SortOrder.Scope#INTRA_FEED} is allowed.
     */
    @Nullable
    private SortOrder sortOrder;

    /**
     * This constructor is used for restoring a feed from the database.
     */
    public Feed(long id, String lastUpdate, String title, String customTitle, String link,
                String description, String paymentLinks, String author, String language,
                String type, String feedIdentifier, String imageUrl, String fileUrl,
                String downloadUrl, boolean downloaded, boolean paged, String nextPageLink,
                String filter, @Nullable SortOrder sortOrder, boolean lastUpdateFailed) {
        super(fileUrl, downloadUrl, downloaded);
        this.id = id;
        this.fundingList = FeedFunding.extractPaymentLinks(paymentLinks);
        this.paged = paged;
        this.nextPageLink = nextPageLink;
        this.items = new ArrayList<>();
        if (filter != null) {
            this.itemfilter = new FeedItemFilter(filter);
        } else {
            this.itemfilter = new FeedItemFilter(new String[0]);
        }
        setSortOrder(sortOrder);
        this.lastUpdateFailed = lastUpdateFailed;
        this.feedDetails = new FeedDetails(lastUpdate, title, customTitle, link, description, author, language, type, feedIdentifier, imageUrl);
    }

    /**
     * This constructor is used for test purposes.
     */
    public Feed(long id, String lastUpdate, String title, String link, String description, String paymentLink,
                String author, String language, String type, String feedIdentifier, String imageUrl, String fileUrl,
                String downloadUrl, boolean downloaded) {
        this(id, lastUpdate, title, null, link, description, paymentLink, author, language, type, feedIdentifier, imageUrl,
                fileUrl, downloadUrl, downloaded, false, null, null, null, false);
    }

    /**
     * This constructor can be used when parsing feed data. Only the 'lastUpdate' and 'items' field are initialized.
     */
    public Feed() {
        super();
    }

    /**
     * This constructor is used for requesting a feed download (it must not be used for anything else!). It should NOT be
     * used if the title of the feed is already known.
     */
    public Feed(String url, String lastUpdate) {
        super(null, url, false);
        feedDetails.setLastUpdate(lastUpdate);
    }

    /**
     * This constructor is used for requesting a feed download (it must not be used for anything else!). It should be
     * used if the title of the feed is already known.
     */
    public Feed(String url, String lastUpdate, String title) {
        this(url, lastUpdate);
        this.feedDetails.setTitle(title);
    }

    /**
     * This constructor is used for requesting a feed download (it must not be used for anything else!). It should be
     * used if the title of the feed is already known.
     */
    public Feed(String url, String lastUpdate, String title, String username, String password) {
        this(url, lastUpdate, title);
//        preferences = new FeedPreferences(0, true, FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, username, password);
    }

    /**
     * Returns the item at the specified index.
     *
     */
    public FeedItem getItemAtIndex(int position) {
        return items.get(position);
    }

    /**
     * Returns the value that uniquely identifies this Feed. If the
     * feedIdentifier attribute is not null, it will be returned. Else it will
     * try to return the title. If the title is not given, it will use the link
     * of the feed.
     */
    public String getIdentifyingValue() {
        if (feedDetails.getFeedIdentifier() != null && !feedDetails.getFeedIdentifier().isEmpty()) {
            return feedDetails.getFeedIdentifier();
        } else if (download_url != null && !download_url.isEmpty()) {
            return download_url;
        } else if (feedDetails.getFeedTitle() != null && !feedDetails.getFeedTitle().isEmpty()) {
            return feedDetails.getFeedTitle();
        } else {
            return feedDetails.getLink();
        }
    }

    @Override
    public String getHumanReadableIdentifier() {
        if (!TextUtils.isEmpty(feedDetails.getCustomTitle())) {
            return feedDetails.getCustomTitle();
        } else if (!TextUtils.isEmpty(feedDetails.getFeedTitle())) {
            return feedDetails.getFeedTitle();
        } else {
            return download_url;
        }
    }

    public void updateFromOther(Feed other) {
        // don't update feed's download_url, we do that manually if redirected
        // see AntennapodHttpClient
        feedDetails.updateFeedDetailsFromOther(other.feedDetails);
        if (other.fundingList != null) {
            fundingList = other.fundingList;
        }
        // this feed's nextPage might already point to a higher page, so we only update the nextPage value
        // if this feed is not paged and the other feed is.
        if (!this.paged && other.paged) {
            this.paged = other.paged;
            this.nextPageLink = other.nextPageLink;
        }
    }

    public boolean compareWithOther(Feed other) {
        if (super.compareWithOther(other)) {
            return true;
        }
        if (other.feedDetails != null) {
            return feedDetails.compareWithOther(other.feedDetails);
        }
        if (other.fundingList != null) {
            if (fundingList == null || !fundingList.equals(other.fundingList)) {
                return true;
            }
        }
        if (other.isPaged() && !this.isPaged()) {
            return true;
        }
        if (!TextUtils.equals(other.getNextPageLink(), this.getNextPageLink())) {
            return true;
        }
        return false;
    }

    public FeedItem getMostRecentItem() {
        // we could sort, but we don't need to, a simple search is fine...
        Date mostRecentDate = new Date(0);
        FeedItem mostRecentItem = null;
        for (FeedItem item : items) {
            if (item.getPubDate() != null && item.getPubDate().after(mostRecentDate)) {
                mostRecentDate = item.getPubDate();
                mostRecentItem = item;
            }
        }
        return mostRecentItem;
    }

    @Override
    public int getTypeAsInt() {
        return FEEDFILETYPE_FEED;
    }


    public List<FeedItem> getItems() {
        return items;
    }

    public void setItems(List<FeedItem> list) {
        this.items = list;
    }

    public void addPayment(FeedFunding funding) {
        if (fundingList == null) {
            fundingList = new ArrayList<FeedFunding>();
        }
        fundingList.add(funding);
    }

    public ArrayList<FeedFunding> getPaymentLinks() {
        return fundingList;
    }

    @Override
    public void setId(long id) {
        super.setId(id);
        if (feedDetails.getPreferences() != null) {
            feedDetails.getPreferences().setFeedID(id);
        }
    }

    public int getPageNr() {
        return pageNr;
    }

    public void setPageNr(int pageNr) {
        this.pageNr = pageNr;
    }

    public boolean isPaged() {
        return paged;
    }

    public void setPaged(boolean paged) {
        this.paged = paged;
    }

    public String getNextPageLink() {
        return nextPageLink;
    }

    public void setNextPageLink(String nextPageLink) {
        this.nextPageLink = nextPageLink;
    }

    @Nullable
    public FeedItemFilter getItemFilter() {
        return itemfilter;
    }

    public void setItemFilter(String[] properties) {
        if (properties != null) {
            this.itemfilter = new FeedItemFilter(properties);
        }
    }

    @Nullable
    public SortOrder getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(@Nullable SortOrder sortOrder) {
        if (sortOrder != null && sortOrder.scope != SortOrder.Scope.INTRA_FEED) {
            throw new IllegalArgumentException("The specified sortOrder " + sortOrder
                    + " is invalid. Only those with INTRA_FEED scope are allowed.");
        }
        this.sortOrder = sortOrder;
    }

    public boolean hasLastUpdateFailed() {
        return this.lastUpdateFailed;
    }

    public void setLastUpdateFailed(boolean lastUpdateFailed) {
        this.lastUpdateFailed = lastUpdateFailed;
    }

    public boolean isLocalFeed() {
        return download_url.startsWith(PREFIX_LOCAL_FOLDER);
    }
}
