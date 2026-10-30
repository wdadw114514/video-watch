package com.example.videowatch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.VideoView;

import java.io.File;
import java.util.Locale;

public class PlayerView extends FrameLayout {

    private final VideoView videoView;
    private final ControlView controls;
    private final Handler handler = new Handler();
    private final AudioManager audioManager;

    private boolean prepared;
    private boolean playing;
    private int duration;
    private boolean controlsVisible = true;
    private String errorMsg;
    private String title = "";

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (playing) {
                controls.invalidate();
                handler.postDelayed(this, 500);
            }
        }
    };

    private final Runnable hideControls = new Runnable() {
        @Override
        public void run() {
            if (playing) {
                controlsVisible = false;
                controls.invalidate();
            }
        }
    };

    public PlayerView(Context context) {
        super(context);
        setBackgroundColor(0xFF000000);
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        videoView = new VideoView(context);
        FrameLayout.LayoutParams vlp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        vlp.gravity = Gravity.CENTER;
        addView(videoView, vlp);

        controls = new ControlView(context);
        addView(controls, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void play(String path) {
        File f = new File(path);
        play(Uri.fromFile(f), f.getName());
    }

    public void play(Uri uri, String name) {
        handler.removeCallbacksAndMessages(null);
        prepared = false;
        playing = false;
        duration = 0;
        controlsVisible = true;
        errorMsg = null;
        title = name == null ? "视频" : name;
        controls.invalidate();

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                prepared = true;
                duration = mp.getDuration();
                videoView.start();
                playing = true;
                handler.post(ticker);
                scheduleHide();
                controls.invalidate();
            }
        });
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                playing = false;
                controlsVisible = true;
                handler.removeCallbacks(hideControls);
                controls.invalidate();
            }
        });
        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                errorMsg = "播放失败 (" + what + "," + extra + ")";
                playing = false;
                prepared = false;
                controlsVisible = true;
                handler.removeCallbacks(hideControls);
                controls.invalidate();
                return true;
            }
        });
        videoView.setVideoURI(uri);
    }

    public void stop() {
        handler.removeCallbacksAndMessages(null);
        try {
            videoView.stopPlayback();
        } catch (Throwable t) {
        }
        playing = false;
        prepared = false;
    }

    void scheduleHide() {
        handler.removeCallbacks(hideControls);
        handler.postDelayed(hideControls, 3200);
    }

    void showControls() {
        controlsVisible = true;
        scheduleHide();
        controls.invalidate();
    }

    void toggleControls() {
        controlsVisible = !controlsVisible;
        if (controlsVisible) {
            scheduleHide();
        }
        controls.invalidate();
    }

    void togglePlay() {
        if (!prepared) {
            return;
        }
        if (videoView.isPlaying()) {
            videoView.pause();
            playing = false;
            showControls();
        } else {
            if (duration > 0 && videoView.getCurrentPosition() >= duration - 250) {
                videoView.seekTo(0);
            }
            videoView.start();
            playing = true;
            handler.post(ticker);
            scheduleHide();
        }
        controls.invalidate();
    }

    void seekTo(int ms) {
        if (!prepared) {
            return;
        }
        if (ms < 0) {
            ms = 0;
        }
        if (ms > duration) {
            ms = duration;
        }
        videoView.seekTo(ms);
    }

    int position() {
        if (!prepared) {
            return 0;
        }
        try {
            return videoView.getCurrentPosition();
        } catch (Throwable t) {
            return 0;
        }
    }

    int getVolume() {
        try {
            return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        } catch (Throwable t) {
            return 0;
        }
    }

    int getMaxVolume() {
        try {
            return Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        } catch (Throwable t) {
            return 1;
        }
    }

    void setVolume(int v) {
        try {
            int max = getMaxVolume();
            if (v < 0) {
                v = 0;
            }
            if (v > max) {
                v = max;
            }
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0);
        } catch (Throwable t) {
        }
    }

    private static String fmt(int ms) {
        if (ms < 0) {
            ms = 0;
        }
        int total = ms / 1000;
        int h = total / 3600;
        int m = (total % 3600) / 60;
        int s = total % 60;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    private class ControlView extends View {

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path tri = new Path();
        private final RectF rect = new RectF();
        private boolean dragging;
        private boolean draggingVol;
        private int lastVol = -1;
        private float downX, downY;

        ControlView(Context c) {
            super(c);
            pt.setTypeface(Typeface.DEFAULT_BOLD);
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            if (errorMsg != null) {
                pt.setTextAlign(Paint.Align.CENTER);
                pt.setTextSize(12);
                pt.setColor(0xFFFF5A5F);
                c.drawText(errorMsg, w / 2f, h / 2f - 8, pt);
                pt.setTextSize(9);
                pt.setColor(0xFF8B93B0);
                c.drawText("返回换一个，或长按列表用系统播放器", w / 2f, h / 2f + 14, pt);
            }
            if (!controlsVisible) {
                return;
            }
            float barTop = h - 88;
            p.setColor(0xCC05070C);
            c.drawRect(0, barTop, w, h, p);

            float volY = h - 74;
            p.setColor(getVolume() > 0 ? 0xFFCCD3E8 : 0xFF6C7392);
            c.drawRect(14, volY - 4, 19, volY + 4, p);
            tri.reset();
            tri.moveTo(19, volY - 4);
            tri.lineTo(26, volY - 9);
            tri.lineTo(26, volY + 9);
            tri.lineTo(19, volY + 4);
            tri.close();
            c.drawPath(tri, p);

            float vsx = 38;
            float vsw = w - vsx - 120;
            p.setColor(0x33FFFFFF);
            rect.set(vsx, volY - 2, vsx + vsw, volY + 2);
            c.drawRoundRect(rect, 2, 2, p);
            float vk = getVolume() / (float) getMaxVolume();
            p.setColor(0xFF2EE6C8);
            rect.set(vsx, volY - 2, vsx + vsw * vk, volY + 2);
            c.drawRoundRect(rect, 2, 2, p);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(vsx + vsw * vk, volY, 4, p);

            pt.setTextAlign(Paint.Align.RIGHT);
            pt.setTextSize(9);
            pt.setColor(0xFFCCD3E8);
            c.drawText(fmt(position()) + "/" + fmt(duration), w - 30, volY + 3, pt);

            float bx = 72;
            float by = h - 40;
            p.setColor(0xFF2EE6C8);
            c.drawCircle(bx, by, 15, p);
            p.setColor(0xFF05070C);
            if (playing) {
                c.drawRect(bx - 6, by - 8, bx - 2, by + 8, p);
                c.drawRect(bx + 2, by - 8, bx + 6, by + 8, p);
            } else {
                tri.reset();
                tri.moveTo(bx - 4, by - 8);
                tri.lineTo(bx + 7, by);
                tri.lineTo(bx - 4, by + 8);
                tri.close();
                c.drawPath(tri, p);
            }

            float sx = 96;
            float sw = w - sx - 96;
            float sy = by;
            p.setColor(0x44FFFFFF);
            rect.set(sx, sy - 3, sx + sw, sy + 3);
            c.drawRoundRect(rect, 3, 3, p);
            float prog = duration > 0 ? Math.min(1f, position() / (float) duration) : 0f;
            p.setColor(0xFF2EE6C8);
            rect.set(sx, sy - 3, sx + sw * prog, sy + 3);
            c.drawRoundRect(rect, 3, 3, p);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(sx + sw * prog, sy, 5, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int w = getWidth();
            int h = getHeight();
            float x = e.getX();
            float y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = x;
                    downY = y;
                    if (controlsVisible && y > h - 88) {
                        if (y <= h - 62) {
                            if (x <= 34) {
                                if (getVolume() > 0) {
                                    lastVol = getVolume();
                                    setVolume(0);
                                } else {
                                    setVolume(lastVol > 0 ? lastVol : getMaxVolume() / 2);
                                }
                                invalidate();
                                return true;
                            }
                            draggingVol = true;
                            doVolume(x);
                            return true;
                        }
                        if (x < 96) {
                            togglePlay();
                            return true;
                        }
                        if (x < w - 96) {
                            dragging = true;
                            doSeek(x);
                            return true;
                        }
                        return true;
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (draggingVol) {
                        doVolume(x);
                    } else if (dragging) {
                        doSeek(x);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (draggingVol) {
                        draggingVol = false;
                        scheduleHide();
                    } else if (dragging) {
                        dragging = false;
                        scheduleHide();
                    } else if (Math.abs(x - downX) < 12 && Math.abs(y - downY) < 12) {
                        if (!controlsVisible) {
                            showControls();
                        } else if (y < h - 88) {
                            toggleControls();
                        }
                    }
                    return true;
                default:
                    return true;
            }
        }

        private void doVolume(float x) {
            int w = getWidth();
            float vsx = 38;
            float vsw = w - vsx - 120;
            float k = (x - vsx) / vsw;
            if (k < 0) {
                k = 0;
            }
            if (k > 1) {
                k = 1;
            }
            setVolume(Math.round(k * getMaxVolume()));
            invalidate();
        }

        private void doSeek(float x) {
            int w = getWidth();
            float sx = 96;
            float sw = w - sx - 96;
            float k = (x - sx) / sw;
            if (k < 0) {
                k = 0;
            }
            if (k > 1) {
                k = 1;
            }
            seekTo((int) (duration * k));
            invalidate();
        }
    }
}
