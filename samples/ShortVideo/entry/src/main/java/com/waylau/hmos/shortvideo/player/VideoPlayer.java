/*
 * Copyright (c) waylau.com, 2023. All rights reserved.
 */

package com.waylau.hmos.shortvideo.player;

import com.waylau.hmos.shortvideo.api.IVideoPlayerLifecycle;
import com.waylau.hmos.shortvideo.api.IVideoPlayer;
import com.waylau.hmos.shortvideo.api.ScreenChangeListener;
import com.waylau.hmos.shortvideo.api.StatuChangeListener;
import com.waylau.hmos.shortvideo.constant.Constants;
import com.waylau.hmos.shortvideo.constant.PlayerStatus;
import com.waylau.hmos.shortvideo.factory.SourceFactory;
import com.waylau.hmos.shortvideo.manager.VideoPlayerLifecycle;
import com.waylau.hmos.shortvideo.util.LogUtil;
import ohos.agp.graphics.Surface;
import ohos.app.Context;
import ohos.app.dispatcher.task.TaskPriority;
import ohos.media.common.Source;
import ohos.media.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Video Player
 *
 * @author <a href="https://waylau.com">Way Lau</a>
 * @since 2023-01-23
 */
public class VideoPlayer implements IVideoPlayer {
    private static final String TAG = VideoPlayer.class.getSimpleName();
    private static final int MICRO_MILLI_RATE = 1000;
    private Player mPlayer;
    private Surface surface;
    private VideoPlayerLifecycle mLifecycle;
    private Builder mBuilder;
    private PlayerStatus mStatu = PlayerStatus.IDEL;
    private float currentVolume = 1;
    private double videoScale = Constants.NUMBER_NEGATIVE_1;
    private boolean isGestureOpen;

    private List<StatuChangeListener> statuChangeCallbacks = new ArrayList<>(0);
    private List<ScreenChangeListener> screenChangeCallbacks = new ArrayList<>(0);

    /**
     * constructor of HmPlayer
     *
     * @param builder builder
     */
    private VideoPlayer(Builder builder) {
        mBuilder = builder;
        mLifecycle = new VideoPlayerLifecycle(this);
    }

    private void initBasePlayer() {
        mPlayer = new Player(mBuilder.mContext);
        Source source = new SourceFactory(mBuilder.mContext, mBuilder.filePath).getSource();
        mPlayer.setSource(source);
        mPlayer.setPlayerCallback(new PlayerCallback());
    }

    /**
     * HmPlayer Callback
     *
     * @since 2020-12-04
     */
    private class PlayerCallback implements Player.IPlayerCallback {
        @Override
        public void onPrepared() {
            LogUtil.info(TAG, "onPrepared is called ");
            for (StatuChangeListener callback : statuChangeCallbacks) {
                mStatu = PlayerStatus.PREPARED;
                callback.statuCallback(PlayerStatus.PREPARED);
            }
        }

        @Override
        public void onMessage(int info, int i1) {
            LogUtil.info(TAG, "onMessage info is " + info + ",i1 is" + i1);
            if (i1 == 0) {
                switch (info) {
                    case Player.PLAYER_INFO_VIDEO_RENDERING_START:
                        for (StatuChangeListener callback : statuChangeCallbacks) {
                            mStatu = PlayerStatus.PLAY;
                            callback.statuCallback(PlayerStatus.PLAY);
                        }
                        if (mBuilder.isPause) {
                            pause();
                        }
                        break;
                    case Player.PLAYER_INFO_BUFFERING_START:
                        for (StatuChangeListener callback : statuChangeCallbacks) {
                            mStatu = PlayerStatus.BUFFERING;
                            callback.statuCallback(PlayerStatus.BUFFERING);
                        }
                        break;
                    case Player.PLAYER_INFO_BUFFERING_END:
                        for (StatuChangeListener callback : statuChangeCallbacks) {
                            mStatu = PlayerStatus.PLAY;
                            callback.statuCallback(PlayerStatus.PLAY);
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        @Override
        public void onError(int type, int extra) {
            LogUtil.info(TAG, "onError is called ,i is " + type + ",i1 is " + extra);
            for (StatuChangeListener callback : statuChangeCallbacks) {
                mStatu = PlayerStatus.ERROR;
                callback.statuCallback(PlayerStatus.ERROR);
            }
            release();
        }

        @Override
        public void onResolutionChanged(int videoX, int videoY) {
            LogUtil.info(TAG, "onResolutionChanged videoX is " + videoX + ",videoY is " + videoY);
            if (!mBuilder.isStretch && videoX != 0 && videoY != 0) {
                videoScale = (double)videoX / videoY;
            }
        }

        @Override
        public void onPlayBackComplete() {
            for (StatuChangeListener callback : statuChangeCallbacks) {
                mStatu = PlayerStatus.COMPLETE;
                callback.statuCallback(PlayerStatus.COMPLETE);
            }
        }

        @Override
        public void onRewindToComplete() {
            resume();
        }

        @Override
        public void onBufferingChange(int value) {}

        @Override
        public void onNewTimedMetaData(Player.MediaTimedMetaData mediaTimedMetaData) {
            LogUtil.info(TAG, "onNewTimedMetaData is called");
        }

        @Override
        public void onMediaTimeIncontinuity(Player.MediaTimeInfo mediaTimeInfo) {
            LogUtil.info(TAG, "onMediaTimeIncontinuity is called");
            for (Player.StreamInfo streanInfo : mPlayer.getStreamInfo()) {
                int streamType = streanInfo.getStreamType();
                if (streamType == Player.StreamInfo.MEDIA_STREAM_TYPE_AUDIO && mStatu == PlayerStatus.PREPARED) {
                    for (StatuChangeListener callback : statuChangeCallbacks) {
                        mStatu = PlayerStatus.PLAY;
                        callback.statuCallback(PlayerStatus.PLAY);
                    }
                    if (mBuilder.isPause) {
                        pause();
                    }
                }
            }
        }
    }

    /**
     * start time consuming operation
     */
    private void start() {
        if (mPlayer != null) {
            mBuilder.mContext.getGlobalTaskDispatcher(TaskPriority.DEFAULT).asyncDispatch(() -> {
                if (surface != null) {
                    mPlayer.setVideoSurface(surface);
                } else {
                    LogUtil.error(TAG, "The surface has not been initialized.");
                }
                mPlayer.prepare();
                if (mBuilder.startMillisecond > 0) {
                    int microsecond = mBuilder.startMillisecond * MICRO_MILLI_RATE;
                    mPlayer.rewindTo(microsecond);
                }
                mPlayer.play();
            });
        }
    }

    @Override
    public IVideoPlayerLifecycle getLifecycle() {
        return mLifecycle;
    }

    @Override
    public void addSurface(Surface videoSurface) {
        this.surface = videoSurface;
    }

    @Override
    public void addPlayerStatuCallback(StatuChangeListener callback) {
        if (callback != null) {
            statuChangeCallbacks.add(callback);
        }
    }

    @Override
    public void removePlayerStatuCallback(StatuChangeListener callback) {
        statuChangeCallbacks.remove(callback);
    }

    @Override
    public void addPlayerViewCallback(ScreenChangeListener callback) {
        if (callback != null) {
            screenChangeCallbacks.add(callback);
        }
    }

    @Override
    public void removePlayerViewCallback(ScreenChangeListener callback) {
        screenChangeCallbacks.remove(callback);
    }

    @Override
    public Builder getBuilder() {
        return mBuilder;
    }

    @Override
    public PlayerStatus getPlayerStatu() {
        return mStatu;
    }

    @Override
    public void resizeScreen(int width, int height) {
        for (ScreenChangeListener screenChangeCallback : screenChangeCallbacks) {
            screenChangeCallback.screenCallback(width, height);
        }
    }

    @Override
    public void openGesture(boolean isOpen) {
        isGestureOpen = isOpen;
    }

    @Override
    public boolean isGestureOpen() {
        return isPlaying() && isGestureOpen;
    }

    @Override
    public void play() {
        if (mPlayer != null) {
            mPlayer.reset();
        }
        for (StatuChangeListener callback : statuChangeCallbacks) {
            mStatu = PlayerStatus.PREPARING;
            callback.statuCallback(PlayerStatus.PREPARING);
        }
        initBasePlayer();
        start();
    }

    @Override
    public void replay() {
        if (isPlaying()) {
            rewindTo(0);
        } else {
            reload(mBuilder.filePath, 0);
        }
    }

    @Override
    public void reload(String filepath, int startMillisecond) {
        mBuilder.filePath = filepath;
        mBuilder.startMillisecond = startMillisecond;
        play();
    }

    @Override
    public void stop() {
        if (mPlayer == null) {
            return;
        }
        mPlayer.stop();
        for (StatuChangeListener callback : statuChangeCallbacks) {
            mStatu = PlayerStatus.STOP;
            callback.statuCallback(PlayerStatus.STOP);
        }
    }

    @Override
    public void release() {
        if (mPlayer == null) {
            return;
        }
        if (mStatu != PlayerStatus.IDEL) {
            videoScale = Constants.NUMBER_NEGATIVE_1;
            mPlayer.release();
            for (StatuChangeListener callback : statuChangeCallbacks) {
                mStatu = PlayerStatus.IDEL;
                callback.statuCallback(PlayerStatus.IDEL);
            }
        }
    }

    @Override
    public void resume() {
        if (mPlayer == null) {
            return;
        }
        if (mStatu != PlayerStatus.IDEL) {
            if (!isPlaying()) {
                mPlayer.play();
            }
            for (StatuChangeListener callback : statuChangeCallbacks) {
                mStatu = PlayerStatus.PLAY;
                callback.statuCallback(PlayerStatus.PLAY);
            }
        }
    }

    @Override
    public void pause() {
        if (mPlayer == null) {
            return;
        }
        if (isPlaying()) {
            mPlayer.pause();
            for (StatuChangeListener callback : statuChangeCallbacks) {
                mStatu = PlayerStatus.PAUSE;
                callback.statuCallback(PlayerStatus.PAUSE);
            }
        }
    }

    @Override
    public int getCurrentPosition() {
        if (mPlayer == null) {
            return 0;
        }
        return mPlayer.getCurrentTime();
    }

    @Override
    public int getDuration() {
        if (mPlayer == null) {
            return 0;
        }
        return mPlayer.getDuration();
    }

    @Override
    public float getVolume() {
        return currentVolume;
    }

    @Override
    public void setVolume(float volume) {
        if (mPlayer != null) {
            if (mPlayer.setVolume(volume)) {
                currentVolume = volume;
            }
        }
    }

    @Override
    public void setPlaySpeed(float speed) {
        if (mPlayer == null) {
            return;
        }
        if (mStatu != PlayerStatus.IDEL) {
            mPlayer.setPlaybackSpeed(speed);
        }
    }

    @Override
    public double getVideoScale() {
        return videoScale;
    }

    @Override
    public boolean isPlaying() {
        if (mPlayer != null) {
            return mPlayer.isNowPlaying();
        }
        return false;
    }

    @Override
    public void rewindTo(int startMicrosecond) {
        if (mPlayer == null) {
            return;
        }
        if (mStatu != PlayerStatus.IDEL) {
            for (StatuChangeListener callback : statuChangeCallbacks) {
                mStatu = PlayerStatus.BUFFERING;
                callback.statuCallback(PlayerStatus.BUFFERING);
            }
            mPlayer.rewindTo(startMicrosecond * MICRO_MILLI_RATE);
        }
    }

    /**
     * Builder
     *
     * @since 2020-12-04
     */
    public static class Builder {
        private Context mContext;
        private String filePath;
        private int startMillisecond;
        private boolean isStretch;
        private boolean isPause;

        /**
         * constructor of Builder
         *
         * @param context context
         */
        public Builder(Context context) {
            mContext = context;
            filePath = "";
            startMillisecond = 0;
        }

        /**
         * setFilePath of Builder
         *
         * @param filePath filePath
         * @return builder
         */
        public Builder setFilePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        /**
         * getFilePath of Builder
         *
         * @return filePath
         */
        public String getFilePath() {
            return filePath;
        }

        /**
         * setStartMillisecond of Builder
         *
         * @param startMillisecond startMillisecond
         * @return builder
         */
        public Builder setStartMillisecond(int startMillisecond) {
            this.startMillisecond = startMillisecond;
            return this;
        }

        /**
         * getStartMillisecond of Builder
         *
         * @return startMillisecond
         */
        public int getStartMillisecond() {
            return startMillisecond;
        }

        /**
         * setStretch of Builder
         *
         * @param isS isStretch
         * @return Builder
         */
        public Builder setStretch(boolean isS) {
            this.isStretch = isS;
            return this;
        }

        /**
         * setPause of Builder
         *
         * @param isP isPause
         * @return Builder
         */
        public Builder setPause(boolean isP) {
            this.isPause = isP;
            return this;
        }

        /**
         * create of Builder
         *
         * @return IPlayer
         */
        public IVideoPlayer create() {
            return new VideoPlayer(this);
        }
    }
}