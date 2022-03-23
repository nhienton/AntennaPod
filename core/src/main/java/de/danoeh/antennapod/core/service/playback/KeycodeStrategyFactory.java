package de.danoeh.antennapod.core.service.playback;

import android.view.KeyEvent;

import de.danoeh.antennapod.playback.base.PlayerStatus;

public class KeycodeStrategyFactory implements IKeycodeStrategyFactory {
    public AbstractKeycodeStrategy createStrategy(int keycode, PlayerStatus status, boolean notificationButton) {
        if (keycode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            return new MediaFastForwardStrategy();
        else if (keycode == KeyEvent.KEYCODE_MEDIA_NEXT)
            return new MediaNextStrategy(status);
        else if (keycode == KeyEvent.KEYCODE_MEDIA_PAUSE)
            return new MediaPauseStrategy(status);
        else if (keycode == KeyEvent.KEYCODE_MEDIA_PLAY)
            return new MediaPlayStrategy(status);
        else if (keycode == KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            return new MediaPreviousStrategy();
        else if (keycode == KeyEvent.KEYCODE_MEDIA_REWIND)
            return new MediaRewindStrategy();
        else if (keycode == KeyEvent.KEYCODE_MEDIA_STOP)
            return new MediaStopStrategy(status);
        else
            return null;
    }
}
