package de.danoeh.antennapod.core.service.playback;

import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer;
import de.danoeh.antennapod.playback.base.PlayerStatus;

public abstract class AbstractKeycodeStrategy {
    protected PlayerStatus status;

    public abstract boolean execute(PlaybackServiceMediaPlayer mediaPlayer);

    protected PlayerStatus getStatus(PlaybackServiceMediaPlayer mediaPlayer) {
        return mediaPlayer.getPlayerStatus();
    }
}
