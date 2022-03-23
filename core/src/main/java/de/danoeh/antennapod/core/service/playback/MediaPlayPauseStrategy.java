package de.danoeh.antennapod.core.service.playback;

import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer;
import de.danoeh.antennapod.playback.base.PlayerStatus;

public class MediaPlayPauseStrategy extends AbstractKeycodeStrategy {
    MediaPlayPauseStrategy(PlayerStatus status) {
        this.status = status;
    }

    public boolean execute(PlaybackServiceMediaPlayer mediaPlayer) {
        if (status == PlayerStatus.PLAYING) {
            mediaPlayer.pause(!UserPreferences.isPersistNotify(), false);
        } else if (status == PlayerStatus.PAUSED || status == PlayerStatus.PREPARED) {
            mediaPlayer.resume();
        } else if (status == PlayerStatus.PREPARING) {
            mediaPlayer.setStartWhenPrepared(!mediaPlayer.isStartWhenPrepared());
        } else if (status == PlayerStatus.INITIALIZED) {
            mediaPlayer.setStartWhenPrepared(true);
            mediaPlayer.prepare();
        } else {
            return false;
        }
        return true;
    }
}
