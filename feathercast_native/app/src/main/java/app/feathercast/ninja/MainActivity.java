package app.feathercast.ninja;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Standalone, native Android camera test for the original CC0 FeatherCast ninja.
 * No browser, internet, Termux, computer, account or OBS required for the test.
 * The phone camera is analyzed locally by bundled ML Kit face detection.
 */
public final class MainActivity extends ComponentActivity {
    private static final int REQUEST_CAMERA = 45;
    private final String[] palettes = { "shadow", "crimson", "forest" };
    private int palette = 0;
    private Bitmap[][] frames = new Bitmap[2][6];
    private FrameLayout root;
    private ImageView ninja;
    private TextView status;
    private Button startButton, previewButton, paletteButton;
    private PreviewView cameraThumbnail;
    private boolean cameraRunning = false;
    private boolean showCamera = false;
    private boolean facePresent = false;
    private long lastFaceTime = 0;
    private float targetYaw = 0, targetPitch = 0, targetRoll = 0;
    private float targetJaw = 0, targetBlink = 0;
    private float smoothYaw = 0, smoothPitch = 0, smoothRoll = 0;
    private float smoothJaw = 0, smoothBlink = 0;
    private int lastFrame = -1;
    private int lastPalette = -1;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService analyzer = Executors.newSingleThreadExecutor();
    private ProcessCameraProvider cameraProvider;
    private FaceDetector faceDetector;

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    private float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13, 20, 33));
        getWindow().setNavigationBarColor(Color.rgb(13, 20, 33));
        getWindow().getDecorView().setSystemUiVisibility(0);
        FaceDetectorOptions opts = new FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .build();
        faceDetector = FaceDetection.getClient(opts);
        buildScreen();
        loadFrames();
        ui.post(tick);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(sp); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }
    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value); b.setAllCaps(false); b.setTextSize(12);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(35, 83, 98)));
        return b;
    }
    private void buildScreen() {
        root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {Color.rgb(17, 29, 49), Color.rgb(9, 17, 29)}));
        setContentView(root);

        // Native ImageView, not WebView or an HTML file.
        ninja = new ImageView(this);
        ninja.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams avatarParams = new FrameLayout.LayoutParams(-1, -1);
        avatarParams.leftMargin = dp(9); avatarParams.rightMargin = dp(9);
        avatarParams.topMargin = dp(90); avatarParams.bottomMargin = dp(150);
        root.addView(ninja, avatarParams);

        // Camera preview is optional; tracking still runs while this thumbnail is hidden.
        cameraThumbnail = new PreviewView(this);
        cameraThumbnail.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        cameraThumbnail.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        FrameLayout.LayoutParams cameraParams = new FrameLayout.LayoutParams(dp(112), dp(142), Gravity.TOP | Gravity.END);
        cameraParams.topMargin = dp(77); cameraParams.rightMargin = dp(12);
        cameraThumbnail.setAlpha(0.0f);
        root.addView(cameraThumbnail, cameraParams);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL); header.setPadding(dp(18), dp(12), dp(18), dp(4));
        TextView title = text("FEATHERCAST  ·  NINJA", 20, Color.rgb(170, 250, 240), true);
        header.addView(title);
        status = text("Ninja ready. Tap Start Camera.", 13, Color.rgb(223, 235, 243), false);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(5); header.addView(status, statusParams);
        FrameLayout.LayoutParams headerParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(header, headerParams);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setPadding(dp(8), dp(9), dp(8), dp(18));
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setBackgroundColor(Color.argb(245, 11, 23, 38));
        TextView hint = text("Move your head, blink and speak. The cloth mask changes size only — no mouth oval or line.",
                12, Color.rgb(204, 223, 233), false);
        hint.setGravity(Gravity.CENTER); hint.setPadding(dp(7), dp(3), dp(7), dp(7));
        bottom.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        startButton = button("Start camera");
        previewButton = button("Show selfie");
        paletteButton = button("Ninja color");
        for (Button b : new Button[] {startButton, previewButton, paletteButton}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(50), 1f);
            p.leftMargin = dp(2); p.rightMargin = dp(2); actions.addView(b, p);
        }
        bottom.addView(actions);
        startButton.setOnClickListener(v -> { if (cameraRunning) stopCamera(); else requestCamera(); });
        previewButton.setOnClickListener(v -> {
            showCamera = !showCamera;
            cameraThumbnail.setAlpha(showCamera ? 1f : 0f);
            previewButton.setText(showCamera ? "Hide selfie" : "Show selfie");
        });
        paletteButton.setOnClickListener(v -> {
            palette = (palette + 1) % palettes.length;
            loadFrames();
            status.setText("Ninja color: " + palettes[palette] + (cameraRunning ? " · camera running" : ""));
        });
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(bottom, bottomParams);
    }

    private void loadFrames() {
        for (Bitmap[] row : frames) for (Bitmap bitmap : row) if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        frames = new Bitmap[2][6];
        for (int blink=0; blink<=1; blink++) for (int jaw=0; jaw<6; jaw++) {
            String file = "ninja/" + palettes[palette] + "-" + blink + "-" + jaw + ".png";
            try (InputStream input = getAssets().open(file)) {
                frames[blink][jaw] = BitmapFactory.decodeStream(input);
            } catch (Exception ignored) { }
        }
        lastFrame = -1; lastPalette = -1;
        updateAvatar(0, 0, 0, 0, 0);
    }

    private void requestCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA);
        } else {
            startCamera();
        }
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_CAMERA) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startCamera();
            else status.setText("Camera permission is needed to track your face.");
        }
    }

    private void startCamera() {
        status.setText("Opening front camera…");
        ListenableFuture<ProcessCameraProvider> provider = ProcessCameraProvider.getInstance(this);
        provider.addListener(() -> {
            try {
                cameraProvider = provider.get();
                cameraProvider.unbindAll();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraThumbnail.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                    .setTargetResolution(new android.util.Size(640,480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
                analysis.setAnalyzer(analyzer, this::analyze);
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis);
                cameraRunning = true;
                startButton.setText("Stop camera");
                status.setText("Camera active. Looking for your face…");
            } catch (Exception ex) {
                cameraRunning = false;
                status.setText("Unable to open front camera: " + ex.getClass().getSimpleName());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyze(ImageProxy proxy) {
        android.media.Image mediaImage = proxy.getImage();
        if (!cameraRunning || mediaImage == null) { proxy.close(); return; }
        InputImage input = InputImage.fromMediaImage(mediaImage, proxy.getImageInfo().getRotationDegrees());
        faceDetector.process(input)
            .addOnSuccessListener(this::handleFaces)
            .addOnFailureListener(err -> ui.post(() -> status.setText("Tracking error: " + err.getClass().getSimpleName())))
            .addOnCompleteListener(task -> proxy.close());
    }

    private static float lipCenterY(FaceContour contour) {
        if (contour == null) return Float.NaN;
        List<PointF> pts = contour.getPoints();
        if (pts == null || pts.isEmpty()) return Float.NaN;
        return pts.get(pts.size() / 2).y;
    }
    private void handleFaces(List<Face> faces) {
        if (faces.isEmpty()) return;
        Face f = faces.get(0);
        float yaw = clamp(f.getHeadEulerAngleY(), -35, 35);
        float pitch = clamp(f.getHeadEulerAngleX(), -25, 25);
        float roll = clamp(f.getHeadEulerAngleZ(), -25, 25);
        Float leftEye = f.getLeftEyeOpenProbability();
        Float rightEye = f.getRightEyeOpenProbability();
        float blink = (leftEye == null || rightEye == null) ? 0 :
            clamp(1f - (leftEye + rightEye) / 2f, 0, 1);

        // Mouth gap is estimated from camera contours, without recording microphone audio.
        float up = lipCenterY(f.getContour(FaceContour.UPPER_LIP_BOTTOM));
        float low = lipCenterY(f.getContour(FaceContour.LOWER_LIP_TOP));
        float jaw = 0;
        if (!Float.isNaN(up) && !Float.isNaN(low)) {
            float gap = Math.abs(low - up) / Math.max(1f, f.getBoundingBox().height());
            jaw = clamp((gap - 0.010f) * 24f, 0, 1);
        }
        final float vJaw = jaw, vBlink = blink;
        ui.post(() -> {
            lastFaceTime = android.os.SystemClock.uptimeMillis();
            if (!facePresent) status.setText("Face detected — move, blink and speak!");
            facePresent = true;
            targetYaw = yaw; targetPitch = pitch; targetRoll = roll;
            targetJaw = vJaw; targetBlink = vBlink;
        });
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!isFinishing() && !isDestroyed()) {
                if (facePresent && android.os.SystemClock.uptimeMillis() - lastFaceTime > 1250) {
                    facePresent = false;
                    targetYaw=0; targetPitch=0; targetRoll=0; targetJaw=0; targetBlink=0;
                    if (cameraRunning) status.setText("Camera active, but face not found. Face the phone.");
                }
                smoothYaw += (targetYaw - smoothYaw) * .30f;
                smoothPitch += (targetPitch - smoothPitch) * .30f;
                smoothRoll += (targetRoll - smoothRoll) * .30f;
                smoothJaw += (targetJaw - smoothJaw) * .35f;
                smoothBlink += (targetBlink - smoothBlink) * .45f;
                updateAvatar(smoothYaw, smoothPitch, smoothRoll, smoothJaw, smoothBlink);
                ui.postDelayed(this, 45);
            }
        }
    };

    private void updateAvatar(float yaw, float pitch, float roll, float jaw, float blink) {
        int j = (int)clamp(Math.round(jaw * 5f),0,5);
        int b = blink > .58f ? 1 : 0;
        int index = b * 6 + j;
        if (lastFrame != index || lastPalette != palette) {
            Bitmap next = frames[b][j];
            if (next != null && !next.isRecycled()) ninja.setImageBitmap(next);
            lastFrame = index; lastPalette = palette;
        }
        ninja.setTranslationX(dp(yaw * .30f));
        ninja.setTranslationY(dp(pitch * .23f));
        ninja.setRotation(roll * .55f);
    }

    private void stopCamera() {
        cameraRunning = false;
        if (cameraProvider != null) cameraProvider.unbindAll();
        facePresent = false;
        targetYaw=0; targetPitch=0; targetRoll=0; targetJaw=0; targetBlink=0;
        startButton.setText("Start camera");
        status.setText("Camera stopped. Ninja preview stays on the phone.");
    }
    @Override protected void onStop() { stopCamera(); super.onStop(); }
    @Override protected void onDestroy() {
        ui.removeCallbacks(tick);
        stopCamera();
        faceDetector.close();
        analyzer.shutdown();
        super.onDestroy();
    }
}