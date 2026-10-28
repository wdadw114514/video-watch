package com.example.videowatch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.widget.VideoView;

import java.util.Locale;

public class PlayerView extends FrameLayout {

    private final VideoView videoView;
    private final ControlView controls;
    private final Handler handler = new Handler();

    private boolean prepared;
    private boolean playing;
    private int duration;
    private boolean controlsVisible = true;

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

        videoView = new VideoView(context);
        videoView.setBackgroundColor(0xFF000000);
        FrameLayout.LayoutParams vlp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        vlp.gravity = Gravity.CENTER;
        addView(videoView, vlp);

        controls = new ControlView(context);
        addView(controls, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void play(String path) {
        handler.removeCallbacksAndMessages(null);
        prepared = false;
        playing = false;
        duration = 0;
        controlsVisible = true;
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
                Toast.makeText(getContext(), "无法播放该视频", Toast.LENGTH_SHORT).show();
                playing = false;
                prepared = false;
                controlsVisible = true;
                controls.invalidate();
                return true;
            }
        });
        videoView.setVideoPath(path);
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
        private float downX, downY;

        ControlView(Context c) {
            super(c);
            pt.setTypeface(Typeface.DEFAULT_BOLD);
        }

        @Override
        protected void onDraw(Canvas c) {
            if (!controlsVisible) {
                return;
            }
            int w = getWidth();
            int h = getHeight();
            float barTop = h - 62;
            p.setColor(0xCC05070C);
            c.drawRect(0, barTop, w, h, p);

            float bx = 28;
            float by = h - 31;
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

            float sx = 52;
            float sw = w - sx - 78;
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

            pt.setTextAlign(Paint.Align.RIGHT);
            pt.setTextSize(10);
            pt.setColor(0xFFCCD3E8);
            c.drawText(fmt(position()) + " / " + fmt(duration), w - 8, sy + 4, pt);
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
                    if (controlsVisible && y > h - 62) {
                        if (x < 48) {
                            togglePlay();
                            return true;
                        }
                        if (x < w - 74) {
                            dragging = true;
                            doSeek(x);
                            return true;
                        }
                        return true;
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        doSeek(x);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        dragging = false;
                        scheduleHide();
                    } else if (Math.abs(x - downX) < 12 && Math.abs(y - downY) < 12) {
                        if (!controlsVisible) {
                            showControls();
                        } else if (y < h - 62) {
                            toggleControls();
                        }
                    }
                    return true;
                default:
                    return true;
            }
        }

        private void doSeek(float x) {
            int w = getWidth();
            float sx = 52;
            float sw = w - sx - 78;
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
