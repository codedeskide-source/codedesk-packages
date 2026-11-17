package app.feathercast.ninja;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;
import com.google.mlkit.vision.pose.PoseDetector;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FeatherCast Ninja v0.7. Everything, including face and shoulder inference,
 * happens on the Android phone. No WebView, browser, network, microphone or OBS.
 * Original sprite is separated into independent overlapping head/torso layers.
 * Distance is estimated RELATIVELY from the apparent size of the face.
 */
public final class MainActivity extends ComponentActivity {
    private static final int CAMERA_REQUEST = 45;
    private static final float ART_HEAD_X = 192f, ART_HEAD_Y = 162f;
    private static final float ART_SHOULDER_X = 192f, ART_SHOULDER_Y = 308f;
    private final String[] palettes = {"shadow", "crimson", "forest"};
    private int palette = 0;
    private final Bitmap[][] heads = new Bitmap[2][6];
    private final Bitmap[][] bodies = new Bitmap[2][6];
    private int currentBlink = 0, currentJaw = 0;
    private FrameLayout root;
    private NinjaView ninjaView;
    private PreviewView previewView;
    private TextView status;
    private Button startButton, modeButton, paletteButton;
    private boolean cameraRunning = false;
    private boolean overlayMode = true;
    private boolean facePresent = false, posePresent = false;
    private long lastFaceTime = 0, lastPoseTime = 0;
    private FaceDetector faceDetector;
    private PoseDetector poseDetector;
    private ProcessCameraProvider cameraProvider;
    private final ExecutorService analyzer = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    // Analysis results (already converted to PreviewView pixels, and mirrored).
    private float targetFaceX, targetFaceY, targetHeadScale = 1f;
    private float targetShoulderX, targetShoulderY, targetBodyScale = 1f;
    private float targetHeadYaw = 0, targetHeadPitch = 0, targetHeadRoll = 0;
    private float targetBodyRoll = 0, targetJaw = 0, targetBlink = 0;
    private float headX, headY, headScale = 1f;
    private float bodyX, bodyY, bodyScale = 1f;
    private float headYaw, headPitch, headRoll, bodyRoll, jaw, blink;
    private boolean trackingInitialized = false;
    private float lastFaceHeight = 100f;

    private static float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }
    private static float approach(float current, float target, float speed) {
        return current + (target - current) * speed;
    }
    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.rgb(13, 20, 33));
        getWindow().setNavigationBarColor(Color.rgb(13, 20, 33));
        faceDetector = FaceDetection.getClient(new FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking().build());
        poseDetector = PoseDetection.getClient(new PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE).build());
        createScreen();
        loadArt();
        ui.post(animationTick);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        return view;
    }
    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value); b.setAllCaps(false); b.setTextSize(12);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(35, 83, 98)));
        return b;
    }
    private void createScreen() {
        root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(17, 29, 49), Color.rgb(9, 17, 29)}));
        setContentView(root);

        // Both the camera and drawn avatar occupy the SAME viewport.
        // Camera pixels never leave the device.
        previewView = new PreviewView(this);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        previewView.setAlpha(0f);
        root.addView(previewView, new FrameLayout.LayoutParams(-1, -1));

        ninjaView = new NinjaView();
        root.addView(ninjaView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setPadding(dp(15), dp(11), dp(15), dp(9));
        top.setBackgroundColor(Color.argb(220, 10, 21, 37));
        top.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("FEATHERCAST  ·  NINJA 0.7", 18, Color.rgb(171, 251, 241));
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        top.addView(title);
        status = text("Native camera test. Start to align ninja over your selfie.", 12, Color.rgb(224, 233, 240));
        status.setPadding(0, dp(4), 0, 0);
        top.addView(status);
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        LinearLayout lower = new LinearLayout(this);
        lower.setOrientation(LinearLayout.VERTICAL);
        lower.setPadding(dp(8), dp(10), dp(8), dp(16));
        lower.setBackgroundColor(Color.argb(235, 10, 21, 37));
        TextView hint = text("Head follows face · torso follows shoulders · step toward or away to resize. Mask cloth only.",
            12, Color.rgb(223, 232, 239));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(6), 0, dp(6), dp(7));
        lower.addView(hint);
        LinearLayout actions = new LinearLayout(this);
        startButton = button("Start camera");
        modeButton = button("Avatar only");
        paletteButton = button("Ninja color");
        for (Button b : new Button[]{startButton, modeButton, paletteButton}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(49), 1);
            p.leftMargin = dp(2); p.rightMargin = dp(2);
            actions.addView(b, p);
        }
        lower.addView(actions);
        root.addView(lower, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        startButton.setOnClickListener(v -> { if (cameraRunning) stopCamera(); else requestCamera(); });
        modeButton.setOnClickListener(v -> {
            overlayMode = !overlayMode;
            previewView.setAlpha(cameraRunning && overlayMode ? 1f : 0f);
            modeButton.setText(overlayMode ? "Avatar only" : "Camera overlay");
            ninjaView.invalidate();
        });
        paletteButton.setOnClickListener(v -> {
            palette = (palette + 1) % palettes.length;
            loadArt();
            status.setText("Ninja color: " + palettes[palette]);
        });
    }

    /** Split existing CC0 artwork into two non-overlapping layers.
     * The cloth mask stays part of the head, with no mouth drawing at all. */
    private void loadArt() {
        for (int b = 0; b < 2; b++) for (int j = 0; j < 6; j++) {
            if (heads[b][j] != null) heads[b][j].recycle();
            if (bodies[b][j] != null) bodies[b][j].recycle();
            heads[b][j] = null; bodies[b][j] = null;
            String file = "ninja/" + palettes[palette] + "-" + b + "-" + j + ".png";
            try (InputStream input = getAssets().open(file)) {
                Bitmap original = BitmapFactory.decodeStream(input);
                if (original == null) continue;
                int w = original.getWidth(), h = original.getHeight();
                int[] inputPixels = new int[w*h], headPixels = new int[w*h], bodyPixels = new int[w*h];
                original.getPixels(inputPixels, 0, w, 0, 0, w, h);
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    // Coordinates are in the artwork's native 384x384 frame.
                    float xx = x * 384f/w, yy = y * 384f/h;
                    boolean inHead = yy < 280f
                        || (yy < 316f && xx >= 68f && xx <= 310f)
                        || (yy < 349f && xx > 286f);
                    int idx = y*w+x;
                    if (inHead) headPixels[idx] = inputPixels[idx];
                    else bodyPixels[idx] = inputPixels[idx];
                }
                heads[b][j] = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                heads[b][j].setPixels(headPixels, 0, w, 0, 0, w, h);
                bodies[b][j] = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                bodies[b][j].setPixels(bodyPixels, 0, w, 0, 0, w, h);
                original.recycle();
            } catch (Exception ex) {
                status.setText("Avatar art error: " + ex.getClass().getSimpleName());
            }
        }
        ninjaView.invalidate();
    }

    private void requestCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
        } else startCamera();
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == CAMERA_REQUEST) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startCamera();
            else status.setText("Camera permission needed for native face/body tracking.");
        }
    }

    private void startCamera() {
        status.setText("Opening Android front camera…");
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                cameraProvider.unbindAll();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                    .setTargetResolution(new Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                analysis.setAnalyzer(analyzer, this::analyze);
                // A common CameraX viewport keeps analysis/preview crops aligned.
                UseCaseGroup.Builder group = new UseCaseGroup.Builder()
                    .addUseCase(preview).addUseCase(analysis);
                if (previewView.getViewPort() != null) group.setViewPort(previewView.getViewPort());
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, group.build());
                cameraRunning = true;
                trackingInitialized = false;
                startButton.setText("Stop camera");
                previewView.setAlpha(overlayMode ? 1f : 0f);
                status.setText("Camera running. Show your face and shoulders.");
            } catch (Exception ex) {
                cameraRunning = false;
                status.setText("Camera error: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }
    private void analyze(ImageProxy proxy) {
        android.media.Image image = proxy.getImage();
        if (!cameraRunning || image == null) { proxy.close(); return; }
        int rotation = proxy.getImageInfo().getRotationDegrees();
        int imageW = (rotation % 180 == 0) ? proxy.getWidth() : proxy.getHeight();
        int imageH = (rotation % 180 == 0) ? proxy.getHeight() : proxy.getWidth();
        InputImage input = InputImage.fromMediaImage(image, rotation);
        Task<List<Face>> faces = faceDetector.process(input);
        Task<Pose> pose = poseDetector.process(input);
        faces.addOnSuccessListener(list -> { if (cameraRunning) trackFace(list, imageW, imageH); });
        pose.addOnSuccessListener(p -> { if (cameraRunning) trackBody(p, imageW, imageH); });
        faces.addOnFailureListener(e -> ui.post(() -> status.setText("Face tracker: " + e.getClass().getSimpleName())));
        pose.addOnFailureListener(e -> ui.post(() -> status.setText("Shoulder tracker: " + e.getClass().getSimpleName())));
        // Keep image open until both asynchronous detectors complete.
        Tasks.whenAllComplete(faces, pose).addOnCompleteListener(t -> proxy.close());
    }

    /** CameraX FILL_CENTER crop and selfie mirroring, shared by face and pose. */
    private PointF map(float imageX, float imageY, int imageW, int imageH) {
        float viewW = Math.max(1, ninjaView.getWidth());
        float viewH = Math.max(1, ninjaView.getHeight());
        float scale = Math.max(viewW/imageW, viewH/imageH);
        return new PointF(viewW/2f - (imageX-imageW/2f)*scale,
            viewH/2f + (imageY-imageH/2f)*scale);
    }
    private float mapScale(int imageW, int imageH) {
        return Math.max((float)Math.max(1,ninjaView.getWidth())/imageW,
            (float)Math.max(1,ninjaView.getHeight())/imageH);
    }
    private static float lipY(FaceContour lip) {
        if (lip == null || lip.getPoints() == null || lip.getPoints().isEmpty()) return Float.NaN;
        List<PointF> pts = lip.getPoints();
        return pts.get(pts.size()/2).y;
    }
    private void trackFace(List<Face> faces, int imageW, int imageH) {
        if (faces.isEmpty()) return;
        Face face = faces.get(0);
        Rect box = face.getBoundingBox();
        PointF center = map(box.exactCenterX(), box.exactCenterY(), imageW, imageH);
        float scale = mapScale(imageW, imageH);
        float faceWidth = box.width()*scale;
        float faceHeight = box.height()*scale;
        Float le = face.getLeftEyeOpenProbability(), re = face.getRightEyeOpenProbability();
        float amountBlink = le == null || re == null ? 0f : clamp(1f-(le+re)/2f,0f,1f);
        float upper = lipY(face.getContour(FaceContour.UPPER_LIP_BOTTOM));
        float lower = lipY(face.getContour(FaceContour.LOWER_LIP_TOP));
        float amountJaw = 0;
        if (!Float.isNaN(upper) && !Float.isNaN(lower))
            amountJaw = clamp((Math.abs(lower-upper)/Math.max(1,box.height())-.01f)*24f, 0f, 1f);
        targetFaceX = center.x; targetFaceY = center.y;
        // A face closer to the phone looks bigger: relative depth, not metric distance.
        targetHeadScale = clamp(faceWidth/210f, .27f, 3.8f);
        lastFaceHeight = faceHeight;
        targetHeadYaw = clamp(face.getHeadEulerAngleY(),-35f,35f);
        targetHeadPitch = clamp(face.getHeadEulerAngleX(),-25f,25f);
        targetHeadRoll = clamp(-face.getHeadEulerAngleZ(),-27f,27f);
        targetJaw = amountJaw; targetBlink = amountBlink;
        if (!facePresent) status.setText("Head and distance tracking active. Looking for shoulders…");
        facePresent = true;
        lastFaceTime = SystemClock.uptimeMillis();
    }
    private void trackBody(Pose pose, int imageW, int imageH) {
        PoseLandmark left = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark right = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
        if (left == null || right == null || left.getInFrameLikelihood() < .55f || right.getInFrameLikelihood() < .55f) return;
        PointF a = map(left.getPosition().x, left.getPosition().y, imageW, imageH);
        PointF b = map(right.getPosition().x, right.getPosition().y, imageW, imageH);
        targetShoulderX = (a.x+b.x)/2f;
        targetShoulderY = (a.y+b.y)/2f;
        float width = (float)Math.hypot(a.x-b.x, a.y-b.y);
        // Torso size is mostly coupled to face depth; shoulders fine-tune it.
        targetBodyScale = clamp(targetHeadScale*.68f + (width/218f)*.32f, .27f,3.8f);
        float lean = (float)Math.toDegrees(Math.atan2(b.y-a.y,b.x-a.x));
        // Shoulder orientation should not flip 180 degrees when labels swap.
        while (lean > 90f) lean -= 180f;
        while (lean < -90f) lean += 180f;
        targetBodyRoll = clamp(lean*.7f,-25f,25f);
        if (!posePresent && facePresent) status.setText("Face + shoulders tracked. Move head and torso separately.");
        posePresent = true;
        lastPoseTime = SystemClock.uptimeMillis();
    }

    private final Runnable animationTick = new Runnable() {
        @Override public void run() {
            if (!isDestroyed() && !isFinishing()) {
                long now = SystemClock.uptimeMillis();
                if (facePresent && now-lastFaceTime>1350) {
                    facePresent=false; targetJaw=targetBlink=targetHeadRoll=targetHeadPitch=targetHeadYaw=0;
                    if (cameraRunning) status.setText("Face out of view. Step into the camera frame.");
                }
                if (posePresent && now-lastPoseTime>1350) posePresent=false;
                if (facePresent) {
                    if (!trackingInitialized) {
                        headX = targetFaceX; headY = targetFaceY; headScale = targetHeadScale;
                        bodyX = targetFaceX;
                        bodyY = targetFaceY + lastFaceHeight*.90f;
                        bodyScale = targetHeadScale;
                        trackingInitialized=true;
                    }
                    headX = approach(headX,targetFaceX,.30f);
                    headY = approach(headY,targetFaceY,.30f);
                    headScale = approach(headScale,targetHeadScale,.20f);
                    float desiredBodyX = posePresent ? targetShoulderX : targetFaceX;
                    float desiredBodyY = posePresent ? targetShoulderY : targetFaceY + lastFaceHeight*.90f;
                    bodyX = approach(bodyX,desiredBodyX,.19f);
                    bodyY = approach(bodyY,desiredBodyY,.19f);
                    bodyScale = approach(bodyScale,posePresent?targetBodyScale:targetHeadScale,.19f);
                }
                headYaw = approach(headYaw,targetHeadYaw,.29f);
                headPitch = approach(headPitch,targetHeadPitch,.29f);
                headRoll = approach(headRoll,targetHeadRoll,.29f);
                bodyRoll = approach(bodyRoll,posePresent?targetBodyRoll:0f,.15f);
                jaw = approach(jaw,targetJaw,.36f);
                blink = approach(blink,targetBlink,.50f);
                currentBlink = blink>.58f?1:0;
                currentJaw = (int)clamp(Math.round(jaw*5f),0,5);
                ninjaView.invalidate();
                ui.postDelayed(this, 44);
            }
        }
    };

    private final class NinjaView extends View {
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        NinjaView() { super(MainActivity.this); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        private void layer(Canvas c, Bitmap bitmap, float x, float y, float scale,
                           float rotation, float pivotX, float pivotY) {
            if (bitmap == null || bitmap.isRecycled()) return;
            c.save();
            c.translate(x,y); c.rotate(rotation);
            c.scale(scale,scale); c.translate(-pivotX,-pivotY);
            c.drawBitmap(bitmap,0,0,paint);
            c.restore();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            Bitmap body = bodies[currentBlink][currentJaw];
            Bitmap head = heads[currentBlink][currentJaw];
            float x,y,bx,by,hs,bs,hr,br;
            if (cameraRunning && facePresent && trackingInitialized && overlayMode) {
                x=headX; y=headY; bx=bodyX; by=bodyY;
                hs=headScale; bs=bodyScale;
                hr=headRoll*.75f; br=bodyRoll;
            } else if (cameraRunning && facePresent && trackingInitialized) {
                // Privacy mode: ninja remains centered on a solid background.
                hs = clamp(headScale,.5f,2f);
                bs = clamp(bodyScale,.5f,2f);
                x=getWidth()*.5f; y=getHeight()*.37f;
                bx=x+(bodyX-headX)*.60f;
                by=y+(bodyY-headY)*.60f;
                hr=headRoll*.75f; br=bodyRoll;
            } else {
                hs=bs=clamp(Math.min(getWidth()/410f,getHeight()/490f),.5f,1.8f);
                x=getWidth()*.5f;
                y=getHeight()*.5f-60f*hs;
                bx=x; by=y+146f*hs;
                hr=br=0;
            }
            // The shoulders have their OWN pivot and transform, independent of the face.
            layer(canvas,body,bx,by,bs,br,ART_SHOULDER_X,ART_SHOULDER_Y);
            // Face yaw/pitch subtly shift the hood only, never the torso.
            layer(canvas,head,x+headYaw*hs*.26f,y+headPitch*hs*.22f,hs,hr,ART_HEAD_X,ART_HEAD_Y);
        }
    }

    private void stopCamera() {
        cameraRunning=false;
        if (cameraProvider!=null) cameraProvider.unbindAll();
        previewView.setAlpha(0f);
        facePresent=false; posePresent=false; trackingInitialized=false;
        targetHeadYaw=targetHeadPitch=targetHeadRoll=targetBodyRoll=targetJaw=targetBlink=0;
        if (startButton!=null) startButton.setText("Start camera");
        if (status!=null) status.setText("Camera stopped. Ninja stays on this phone.");
    }
    @Override protected void onStop() { stopCamera(); super.onStop(); }
    @Override protected void onDestroy() {
        ui.removeCallbacks(animationTick);
        stopCamera();
        faceDetector.close(); poseDetector.close(); analyzer.shutdown();
        super.onDestroy();
    }
}