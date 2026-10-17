package dev.rift.launcher.stream

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import androidx.core.content.edit

/**
 * The GPU's GL_RENDERER string, which Artemis' MediaCodecHelper uses to pick decoder quirks
 * (Adreno, PowerVR). Artemis reads it from a GLSurfaceView on its PC list; Rift has no GL view,
 * so it asks EGL once with an offscreen surface and keeps the answer.
 */
object GlRenderer {
    private const val PREFS = "rift_stream_gl"
    private const val KEY_RENDERER = "gl_renderer"
    private const val KEY_FINGERPRINT = "fingerprint"

    fun get(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fingerprint = android.os.Build.FINGERPRINT
        val cached = prefs.getString(KEY_RENDERER, null)
        if (cached != null && prefs.getString(KEY_FINGERPRINT, null) == fingerprint) return cached
        val renderer = runCatching { query() }.getOrNull().orEmpty()
        prefs.edit {
            putString(KEY_RENDERER, renderer)
            putString(KEY_FINGERPRINT, fingerprint)
        }
        return renderer
    }

    private fun query(): String? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null
        val version = IntArray(2)
        // No eglTerminate afterwards: the display is shared with the rest of the process.
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val attribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) || count[0] == 0) return null
        val config = configs[0]
        val context = EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
            0,
        )
        if (context == EGL14.EGL_NO_CONTEXT) return null
        val surface = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        try {
            if (surface == EGL14.EGL_NO_SURFACE || !EGL14.eglMakeCurrent(display, surface, surface, context)) return null
            return GLES20.glGetString(GLES20.GL_RENDERER)
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
        }
    }
}
