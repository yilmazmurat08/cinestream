package com.example

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient

import com.example.data.api.NetworkModule
import com.example.util.CrashRecoveryManager
import com.example.util.StorageOptimizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class IPTVApplication : Application(), ImageLoaderFactory {

    companion object {
        val applicationScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    }

    override fun onCreate() {
        super.onCreate()

        // Sadece debug: ana iş parçacığındaki disk/ağ işlerini ve sızıntıları Logcat'e yazar (StrictMode etiketi).
        if (BuildConfig.DEBUG) {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build()
            )
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedSqlLiteObjects()
                    .detectActivityLeaks()
                    .detectLeakedRegistrationObjects()
                    .penaltyLog()
                    .build()
            )
        }

        // 1. Initialize crash recovery & safe crash telemetry logging first
        CrashRecoveryManager.onAppStart(this, applicationScope)

        // Setup WebView multi-process safety
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                val processName = getProcessName()
                if (packageName != processName) {
                    android.webkit.WebView.setDataDirectorySuffix(processName)
                }
            } catch (e: Throwable) {
                // Safe catch
            }
        }

        // 2. Explicitly set the custom ImageLoader globally
        Coil.setImageLoader(this)

        // 3. Initialize RevenueCat SDK
        com.example.util.SubscriptionManager.initRevenueCat(this, "REVENUECAT_API_KEY")

        // 4. Perform proactive cache cleanup & storage maintenance on app startup
        applicationScope.launch {
            StorageOptimizer.performStartupMaintenance(this@IPTVApplication)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
                Coil.imageLoader(this).memoryCache?.clear()
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            Coil.imageLoader(this).memoryCache?.clear()
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    override fun newImageLoader(): ImageLoader {
        val activityManager = getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val totalRamGB = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)

        val (memoryCachePercent, diskCacheMB) = when {
            totalRamGB < 4.5 -> 0.15 to 100L
            totalRamGB < 8.0 -> 0.20 to 150L
            else -> 0.28 to 250L
        }
        android.util.Log.d("IPTVApplication", "Cihaz RAM: %.1f GB -> önbellek: %%${(memoryCachePercent * 100).toInt()} RAM, ${diskCacheMB}MB disk".format(totalRamGB))

        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(memoryCachePercent)
                    .build()
            }
            .diskCache {
                try {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(diskCacheMB * 1024 * 1024)
                        .build()
                } catch (e: Exception) {
                    null
                }
            }
            .crossfade(true)
            .allowRgb565(true)
            .okHttpClient {
                NetworkModule.provideOkHttpClient()
            }
            .build()
    }
}

