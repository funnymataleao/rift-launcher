package com.winlator.renderer;

import androidx.annotation.Keep;

import com.winlator.xserver.Drawable;

import java.nio.ByteBuffer;

public class GPUImage extends Texture {
    private static boolean supported = false;
    private long hardwareBufferPtr;
    private long imageKHRPtr;
    private boolean locked;
    private int nativeHandle;
    private short stride;
    private ByteBuffer virtualData;

    private native long createHardwareBuffer(short s, short s2, boolean z, boolean z2);

    private native long createImageKHR(long j, int i);

    private native void destroyHardwareBuffer(long j, boolean z);

    private native void destroyImageKHR(long j);

    private native ByteBuffer lockHardwareBuffer(long j);

    static {
        System.loadLibrary("winlator_11");
    }

    public GPUImage(short width, short height) {
        this(width, height, true, true);
    }

    public GPUImage(short width, short height, boolean cpuAccess) {
        this(width, height, cpuAccess, true);
    }

    public GPUImage(short width, short height, boolean cpuAccess, boolean useHALPixelFormatBGRA8888) {
        this.locked = false;
        long jCreateHardwareBuffer = createHardwareBuffer(width, height, cpuAccess, useHALPixelFormatBGRA8888);
        this.hardwareBufferPtr = jCreateHardwareBuffer;
        if (cpuAccess && jCreateHardwareBuffer != 0) {
            this.virtualData = lockHardwareBuffer(jCreateHardwareBuffer);
            this.locked = true;
        }
    }

    @Override
    public void allocateTexture(short width, short height, ByteBuffer data) {
        if (isAllocated()) {
            return;
        }
        super.allocateTexture(width, height, null);
        this.imageKHRPtr = createImageKHR(this.hardwareBufferPtr, this.textureId);
    }

    @Override
    public void updateFromDrawable(Drawable drawable) {
        if (!isAllocated()) {
            allocateTexture(drawable.width, drawable.height, null);
        }
        this.needsUpdate = false;
    }

    public short getStride() {
        return this.stride;
    }

    @Keep
    private void setStride(short stride) {
        this.stride = stride;
    }

    public int getNativeHandle() {
        return this.nativeHandle;
    }

    @Keep
    private void setNativeHandle(int nativeHandle) {
        this.nativeHandle = nativeHandle;
    }

    public ByteBuffer getVirtualData() {
        return this.virtualData;
    }

    @Override
    public void destroy() {
        destroyImageKHR(this.imageKHRPtr);
        destroyHardwareBuffer(this.hardwareBufferPtr, this.locked);
        this.virtualData = null;
        this.imageKHRPtr = 0L;
        this.hardwareBufferPtr = 0L;
        super.destroy();
    }

    public static boolean isSupported() {
        return supported;
    }

    public long getHardwareBufferPtr() {
        return this.hardwareBufferPtr;
    }

    public static void checkIsSupported() {
        GPUImage gpuImage = new GPUImage((short) 8, (short) 8);
        gpuImage.allocateTexture((short) 8, (short) 8, null);
        supported = (gpuImage.hardwareBufferPtr == 0 || gpuImage.imageKHRPtr == 0 || gpuImage.virtualData == null) ? false : true;
        gpuImage.destroy();
    }
}
