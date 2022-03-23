package de.danoeh.antennapod.core.widget;

public class MediaInfo {
    public int position;
    public int duration;
    public float playbackSpeed;

    public MediaInfo(int position, int duration, float playbackSpeed) {
        this.position = position;
        this.duration = duration;
        this.playbackSpeed = playbackSpeed;
    }

}
