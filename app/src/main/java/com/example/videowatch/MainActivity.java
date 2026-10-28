package com.example.videowatch;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String[] EXT = {
            ".mp4", ".mkv", ".avi", ".3gp", ".webm", ".flv", ".mov",
            ".ts", ".m4v", ".wmv", ".rmvb", ".mpg", ".mpeg", ".rm"
    };

    private LinearLayout listPage;
    private PlayerView playerPage;
    private ListView listView;
    private TextView titleView;
    private TextView hintView;
    private VideoAdapter adapter;
    private final ArrayList<File> videos = new ArrayList<File>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0D14);

        listPage = buildListPage();
        root.addView(listPage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        playerPage = new PlayerView(this);
        playerPage.setVisibility(View.GONE);
        root.addView(playerPage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        hideSystemUi();
        scanVideos();
    }

    private LinearLayout buildListPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF0B0D14);

        titleView = new TextView(this);
        titleView.setText("扫描中...");
        titleView.setTextSize(13);
        titleView.setTextColor(0xFF2EE6C8);
        titleView.setPadding(22, 24, 22, 6);
        page.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        hintView = new TextView(this);
        hintView.setText("点击播放 · 自动适配屏幕");
        hintView.setTextSize(9);
        hintView.setTextColor(0xFF5A6280);
        hintView.setPadding(22, 0, 22, 8);
        page.addView(hintView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        listView = new ListView(this);
        listView.setBackgroundColor(0xFF0B0D14);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setCacheColorHint(0);
        listView.setSelector(new ColorDrawable(0x33FFFFFF));
        listView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        adapter = new VideoAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < videos.size()) {
                    openVideo(videos.get(position));
                }
            }
        });
        page.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    private void openVideo(File f) {
        listPage.setVisibility(View.GONE);
        playerPage.setVisibility(View.VISIBLE);
        playerPage.play(f.getAbsolutePath());
    }

    @Override
    public void onBackPressed() {
        if (playerPage.getVisibility() == View.VISIBLE) {
            playerPage.stop();
            playerPage.setVisibility(View.GONE);
            listPage.setVisibility(View.VISIBLE);
            hideSystemUi();
        } else {
            super.onBackPressed();
        }
    }

    private void hideSystemUi() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    private void scanVideos() {
        titleView.setText("扫描中...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<File> found = new ArrayList<File>();
                File base = Environment.getExternalStorageDirectory();
                if (base != null) {
                    walk(base, found, 0);
                }
                Collections.sort(found, new Comparator<File>() {
                    @Override
                    public int compare(File a, File b) {
                        long d = b.lastModified() - a.lastModified();
                        return d > 0 ? 1 : (d < 0 ? -1 : 0);
                    }
                });
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        videos.clear();
                        videos.addAll(found);
                        adapter.notifyDataSetChanged();
                        if (found.isEmpty()) {
                            titleView.setText("没找到视频文件");
                            hintView.setText("把视频放到存储卡里再打开本应用");
                        } else {
                            titleView.setText("视频 · " + found.size());
                            hintView.setText("点击播放 · 自动适配屏幕");
                        }
                    }
                });
            }
        }).start();
    }

    private void walk(File dir, ArrayList<File> out, int depth) {
        if (depth > 6 || out.size() >= 400) {
            return;
        }
        File[] files;
        try {
            files = dir.listFiles();
        } catch (Throwable t) {
            return;
        }
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (out.size() >= 400) {
                return;
            }
            String name = f.getName();
            if (name.startsWith(".")) {
                continue;
            }
            if (f.isDirectory()) {
                if (name.equalsIgnoreCase("Android") || name.equalsIgnoreCase("obb")
                        || name.equalsIgnoreCase("LOST.DIR")) {
                    continue;
                }
                walk(f, out, depth + 1);
            } else if (isVideo(name)) {
                out.add(f);
            }
        }
    }

    private boolean isVideo(String name) {
        String lower = name.toLowerCase(Locale.US);
        for (String e : EXT) {
            if (lower.endsWith(e)) {
                return true;
            }
        }
        return false;
    }

    private String formatSize(long bytes) {
        if (bytes >= 1073741824L) {
            return String.format(Locale.US, "%.2f GB", bytes / 1073741824.0);
        }
        if (bytes >= 1048576L) {
            return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
        }
        return String.format(Locale.US, "%d KB", Math.max(1, bytes / 1024));
    }

    private class VideoAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return videos.size();
        }

        @Override
        public Object getItem(int position) {
            return videos.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout item;
            if (convertView == null) {
                item = new LinearLayout(MainActivity.this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setPadding(22, 9, 22, 9);
                item.setMinimumHeight(50);

                TextView name = new TextView(MainActivity.this);
                name.setTextSize(12);
                name.setTextColor(0xFFFFFFFF);
                name.setSingleLine(true);
                name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                item.addView(name, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView info = new TextView(MainActivity.this);
                info.setTextSize(9);
                info.setTextColor(0xFF7A84A6);
                info.setSingleLine(true);
                item.addView(info, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            } else {
                item = (LinearLayout) convertView;
            }
            File f = videos.get(position);
            ((TextView) item.getChildAt(0)).setText(f.getName());
            ((TextView) item.getChildAt(1)).setText(formatSize(f.length()) + "  " + f.getParent());
            return item;
        }
    }
}
