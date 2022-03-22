package de.danoeh.antennapod.model.feed;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.util.ArrayList;

public class FeedDetails {
    /**
     * Contains 'id'-element in Atom feed.
     */
    private String feedIdentifier;
    /**
     * title as defined by the feed.
     */
    private String feedTitle;

    /**
     * custom title set by the user.
     */
    private String customTitle;

    /**
     * Link to the website.
     */
    private String link;
    private String description;
    private String language;
    /**
     * Name of the author.
     */
    private String author;
    private String imageUrl;
    /**
     * String that identifies the last update (adopted from Last-Modified or ETag header).
     */
    private String lastUpdate;
    /**
     * Feed type, for example RSS 2 or Atom.
     */
    private String type;

    private FeedPreferences preferences;

    public FeedDetails(String lastUpdate, String title, String customTitle, String link,
                String description, String author, String language,
                String type, String feedIdentifier, String imageUrl) {
        this.feedTitle = title;
        this.customTitle = customTitle;
        this.lastUpdate = lastUpdate;
        this.link = link;
        this.description = description;
        this.author = author;
        this.language = language;
        this.type = type;
        this.feedIdentifier = feedIdentifier;
        this.imageUrl = imageUrl;
    }

    public String getFeedIdentifier() {
        return feedIdentifier;
    }

    public void setFeedIdentifier(String feedIdentifier) {
        this.feedIdentifier = feedIdentifier;
    }

    public String getTitle() {
        return !TextUtils.isEmpty(customTitle) ? customTitle : feedTitle;
    }

    public void setTitle(String title) {
        this.feedTitle = title;
    }

    public String getFeedTitle() {
        return this.feedTitle;
    }

    @Nullable
    public String getCustomTitle() {
        return this.customTitle;
    }

    public void setCustomTitle(String customTitle) {
        if (customTitle == null || customTitle.equals(feedTitle)) {
            this.customTitle = null;
        } else {
            this.customTitle = customTitle;
        }
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(String lastModified) {
        this.lastUpdate = lastModified;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setPreferences(FeedPreferences preferences) {
        this.preferences = preferences;
    }

    public FeedPreferences getPreferences() {
        return preferences;
    }

    public void updateFeedDetailsFromOther(FeedDetails other) {
        if (other.imageUrl != null) {
            this.imageUrl = other.imageUrl;
        }
        if (other.feedTitle != null) {
            feedTitle = other.feedTitle;
        }
        if (other.feedIdentifier != null) {
            feedIdentifier = other.feedIdentifier;
        }
        if (other.link != null) {
            link = other.link;
        }
        if (other.description != null) {
            description = other.description;
        }
        if (other.language != null) {
            language = other.language;
        }
        if (other.author != null) {
            author = other.author;
        }
    }

    public boolean compareWithOther(FeedDetails other) {
        if (other.imageUrl != null) {
            if (imageUrl == null || !TextUtils.equals(imageUrl, other.imageUrl)) {
                return true;
            }
        }
        if (!TextUtils.equals(feedTitle, other.feedTitle)) {
            return true;
        }
        if (other.feedIdentifier != null) {
            if (feedIdentifier == null || !feedIdentifier.equals(other.feedIdentifier)) {
                return true;
            }
        }
        if (other.link != null) {
            if (link == null || !link.equals(other.link)) {
                return true;
            }
        }
        if (other.description != null) {
            if (description == null || !description.equals(other.description)) {
                return true;
            }
        }
        if (other.language != null) {
            if (language == null || !language.equals(other.language)) {
                return true;
            }
        }
        if (other.author != null) {
            if (author == null || !author.equals(other.author)) {
                return true;
            }
        }
        return false;
    }
}
