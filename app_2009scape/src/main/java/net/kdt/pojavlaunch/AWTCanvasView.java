package net.kdt.pojavlaunch;

import android.content.*;
import android.graphics.*;
import android.os.Build;
import android.text.*;
import android.util.*;
import android.view.*;

import androidx.annotation.RequiresApi;

import java.util.*;
import net.kdt.pojavlaunch.utils.*;
import org.lwjgl.glfw.*;

public class AWTCanvasView extends TextureView implements TextureView.SurfaceTextureListener, Runnable {
    private float mScaleFactor;
    private float[] mScales;
    private boolean mIsDestroyed = false;
    
    private final TextPaint fpsPaint;
    private boolean attached = false;

    // Temporary count fps https://stackoverflow.com/a/13729241
    private final LinkedList<Long> times = new LinkedList<Long>(){{add(System.nanoTime());}};

    /** Calculates and returns frames per second */
    private double fps() {
        long lastTime = System.nanoTime();
        final double NANOS = 1000000000.0;
        double difference = (lastTime - times.getFirst()) / NANOS;
        times.addLast(lastTime);
        int size = times.size();
        int MAX_SIZE = 100;
        if (size > MAX_SIZE) {
            times.removeFirst();
        }
        return difference > 0 ? times.size() / difference : 0.0;
    }

    /** Computes the scale to better fit the screen */
    void initScaleFactors(){
        initScaleFactors(0);
    }

    void initScaleFactors(float forcedScale){
        //Could be optimized
        if(forcedScale < 1) { //Auto scale
            mScaleFactor = 2.166F;
        }else{
            mScaleFactor = forcedScale;
        }

        float[] scales = new float[2]; //Left, Top

        scales[0] = (CallbackBridge.physicalWidth/2f);
        scales[0] -= scales[0]/mScaleFactor;

        scales[1] = (CallbackBridge.physicalHeight/2f);
        scales[1] -= scales[1]/mScaleFactor;

        mScales = scales;
    }
    
    public AWTCanvasView(Context ctx) {
        this(ctx, null);
    }
    
    public AWTCanvasView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        fpsPaint = new TextPaint();
        fpsPaint.setColor(Color.WHITE);
        fpsPaint.setTextSize(20);
        setSurfaceTextureListener(this);
        initScaleFactors();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int w, int h) {
        mIsDestroyed = false;
        new Thread(this, "AndroidAWTRenderer").start();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        mIsDestroyed = true;
        return true;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int w, int h) {
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void run() {
        Canvas canvas;
        final Surface mSurface = new Surface(getSurfaceTexture());
        Paint p = new Paint();
        p.setAntiAlias(false);
        p.setDither(false);
        p.setFilterBitmap(false);
        int width = CallbackBridge.physicalWidth;
        int height = CallbackBridge.physicalHeight;
        int[] rgbArray;
        Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            while (!mIsDestroyed) {
                if (!attached) {
                    attached = CallbackBridge.nativeAttachThreadToOther(true, BaseMainActivity.isInputStackCall);
                } else {
                    canvas = mSurface.lockHardwareCanvas();
                    //canvas = mSurface.lockCanvas(null);
                    canvas.drawRGB(0, 0, 0);
                    rgbArray = JREUtils.renderAWTScreenFrame(/* canvas, mWidth, mHeight */);
                    if (rgbArray != null) {
                        canvas.save();
                        canvas.scale(mScaleFactor, mScaleFactor);
                        canvas.translate(-mScales[0],-mScales[1]);
                        b.setPixels(rgbArray,0,width,0,0,width,height);
                        canvas.drawBitmap(b,0,0,p);
                        canvas.restore();
                        canvas.drawText("FPS: " + (Math.round(fps() * 10) / 10) + ", attached=" + attached + ", HWA: "+ canvas.isHardwareAccelerated(), 50, 50, fpsPaint);
                    }
                    mSurface.unlockCanvasAndPost(canvas);
                }
            }
            mSurface.release();
        } catch (Throwable th) {
            Tools.showError(getContext(), th);
        }
    }
}
