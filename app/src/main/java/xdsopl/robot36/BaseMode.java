package xdsopl.robot36;

import android.graphics.Bitmap;

public abstract class BaseMode implements Mode {
	@Override
	public Bitmap postProcessScopeImage(Bitmap bmp) {
		return bmp;
	}
}
