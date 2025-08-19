package xdsopl.robot36;

import android.graphics.Bitmap;

public abstract class BaseMode implements Mode {
	@Override
	public Bitmap postProcessScopeImage(Bitmap bmp) {
		return Bitmap.createScaledBitmap(bmp, bmp.getWidth() / 3, bmp.getHeight() / 3, true);
	}
}
