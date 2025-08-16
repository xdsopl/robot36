package xdsopl.robot36;

import android.graphics.Bitmap;

public abstract class BaseMode implements Mode {
    @Override
    public int getEstimatedHorizontalShift() {
        return 0;
    }

    @Override
    public Bitmap postProcessScopeImage(Bitmap bmp) {
        return Bitmap.createScaledBitmap(bmp, bmp.getWidth() / 3, bmp.getHeight() / 3, true);
    }
}
