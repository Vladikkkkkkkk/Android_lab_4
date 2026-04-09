package com.example.lab4;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private RadioGroup rgMediaType;
    private RadioButton rbAudio, rbVideo;
    private PlayerView playerView;
    private TextView tvFileName, tvStatus, tvCurrentTime, tvTotalTime;
    private SeekBar seekBar;
    private Button btnPlay, btnPause, btnStop, btnPickFile, btnRawFile, btnUrlDialog, btnLoadUrl;
    private LinearLayout layoutUrl;
    private EditText etUrl;

    private ExoPlayer exoPlayer;
    private boolean isAudioMode = true;

    private final Handler handler = new Handler();
    private Runnable updateSeekBar;

    private ActivityResultLauncher<String[]> filePickerLauncher;

    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        initViews();
        initPlayer();
        setupListeners();
        setupFilePicker();
        setupPermissions();
    }

    private void initViews() {
        rgMediaType   = findViewById(R.id.rgMediaType);
        rbAudio       = findViewById(R.id.rbAudio);
        rbVideo       = findViewById(R.id.rbVideo);
        playerView    = findViewById(R.id.playerView);
        tvFileName    = findViewById(R.id.tvFileName);
        tvStatus      = findViewById(R.id.tvStatus);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime   = findViewById(R.id.tvTotalTime);
        seekBar       = findViewById(R.id.seekBar);
        btnPlay       = findViewById(R.id.btnPlay);
        btnPause      = findViewById(R.id.btnPause);
        btnStop       = findViewById(R.id.btnStop);
        btnPickFile   = findViewById(R.id.btnPickFile);
        btnUrlDialog  = findViewById(R.id.btnUrlDialog);
        btnLoadUrl    = findViewById(R.id.btnLoadUrl);
        btnRawFile    = findViewById(R.id.btnRawFile);
        layoutUrl     = findViewById(R.id.layoutUrl);
        etUrl         = findViewById(R.id.etUrl);
    }

    private void initPlayer() {
        exoPlayer = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(exoPlayer);

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    long duration = exoPlayer.getDuration();
                    seekBar.setMax((int) duration);
                    tvTotalTime.setText(formatTime(duration));
                    setControlsEnabled(true);
                    setStatus("Готово до відтворення");
                } else if (state == Player.STATE_ENDED) {
                    setStatus("Відтворення завершено");
                    seekBar.setProgress(0);
                    tvCurrentTime.setText("0:00");
                } else if (state == Player.STATE_BUFFERING) {
                    setStatus("Буферизація...");
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    setStatus("▶ Відтворення...");
                    startSeekBarUpdate();
                } else {
                    handler.removeCallbacks(updateSeekBar);
                }
            }
        });

        updateSeekBar = new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null && exoPlayer.isPlaying()) {
                    long pos = exoPlayer.getCurrentPosition();
                    seekBar.setProgress((int) pos);
                    tvCurrentTime.setText(formatTime(pos));
                    handler.postDelayed(this, 500);
                }
            }
        };
    }

    private void setupListeners() {
        rgMediaType.setOnCheckedChangeListener((group, checkedId) -> {
            isAudioMode = (checkedId == R.id.rbAudio);
            playerView.setVisibility(isAudioMode ? View.GONE : View.VISIBLE);
            stopMedia();
        });

        btnPickFile.setOnClickListener(v -> requestPermissionsAndPick());
        btnRawFile.setOnClickListener(v -> playFromRawStorage());

        btnUrlDialog.setOnClickListener(v -> {
            layoutUrl.setVisibility(
                    layoutUrl.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE
            );
        });

        btnLoadUrl.setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(this, "Введіть URL!", Toast.LENGTH_SHORT).show();
                return;
            }
            loadAndPlay(Uri.parse(url), url);
            layoutUrl.setVisibility(View.GONE);
        });

        btnPlay.setOnClickListener(v -> {
            exoPlayer.play();
            setStatus("▶ Відтворення...");
        });

        btnPause.setOnClickListener(v -> {
            exoPlayer.pause();
            setStatus("⏸ Пауза");
        });

        btnStop.setOnClickListener(v -> stopMedia());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser) exoPlayer.seekTo(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    private void setupFilePicker() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        getContentResolver().takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
                        String name = getFileNameFromUri(uri);
                        loadAndPlay(uri, name);
                    }
                }
        );
    }

    private void setupPermissions() {
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean granted = true;
                    for (Boolean v : result.values()) if (!v) { granted = false; break; }
                    if (granted) openFilePicker();
                    else Toast.makeText(this, "Потрібні дозволи для читання файлів", Toast.LENGTH_SHORT).show();
                }
        );
    }

    private void requestPermissionsAndPick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String[] perms = isAudioMode
                    ? new String[]{Manifest.permission.READ_MEDIA_AUDIO}
                    : new String[]{Manifest.permission.READ_MEDIA_VIDEO};

            boolean allGranted = true;
            for (String p : perms)
                if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                { allGranted = false; break; }

            if (allGranted) openFilePicker();
            else permissionLauncher.launch(perms);
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                openFilePicker();
            } else {
                permissionLauncher.launch(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE});
            }
        }
    }

    private void openFilePicker() {
        String[] mimeTypes = isAudioMode
                ? new String[]{"audio/*"}
                : new String[]{"video/*"};
        filePickerLauncher.launch(mimeTypes);
    }

    private void loadAndPlay(Uri uri, String displayName) {
        stopMedia();
        tvFileName.setText("📄 " + displayName);
        MediaItem mediaItem = MediaItem.fromUri(uri);
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();
        exoPlayer.play();
        setStatus("⏳ Завантаження...");
    }

    private void playFromRawStorage() {
        int rawResId = isAudioMode ? R.raw.sample_audio : R.raw.sample_video;

        if (rawResId == 0) {
            Toast.makeText(this,
                    "Файл не знайдено у res/raw! Додай sample_audio.mp3 або sample_video.mp4",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Uri rawUri = Uri.parse(
                "android.resource://" + getPackageName() + "/" + rawResId
        );

        String label = isAudioMode ? "sample_audio.mp3 (raw)" : "sample_video.mp4 (raw)";
        loadAndPlay(rawUri, label);

        Toast.makeText(this, "▶ Грає з внутрішнього сховища", Toast.LENGTH_SHORT).show();
    }
    private void stopMedia() {
        exoPlayer.stop();
        exoPlayer.clearMediaItems();
        seekBar.setProgress(0);
        tvCurrentTime.setText("0:00");
        tvTotalTime.setText("0:00");
        setControlsEnabled(false);
        setStatus("⏹ Зупинено");
        handler.removeCallbacks(updateSeekBar);
    }

    private void startSeekBarUpdate() {
        handler.removeCallbacks(updateSeekBar);
        handler.post(updateSeekBar);
    }

    private void setControlsEnabled(boolean enabled) {
        btnPlay.setEnabled(enabled);
        btnPause.setEnabled(enabled);
        btnStop.setEnabled(enabled);
    }

    private void setStatus(String text) {
        tvStatus.setText("Статус: " + text);
    }

    private String getFileNameFromUri(Uri uri) {
        String path = uri.getPath();
        if (path == null) return uri.toString();
        int cut = path.lastIndexOf('/');
        return cut >= 0 ? path.substring(cut + 1) : path;
    }

    private String formatTime(long millis) {
        if (millis < 0) return "0:00";
        long min = TimeUnit.MILLISECONDS.toMinutes(millis);
        long sec = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        return String.format("%d:%02d", min, sec);
    }

    @Override
    protected void onPause() {
        super.onPause();
        exoPlayer.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateSeekBar);
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }
}