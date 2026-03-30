package com.tinybear.chatyinput.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.tinybear.chatyinput.model.LocationData

// 位置提供器：使用 FusedLocationProviderClient 获取粗略位置
class LocationProvider(private val context: Context) {
    companion object {
        private const val TAG = "LocationProvider"
        private const val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 分钟缓存
    }

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var cachedLocation: LocationData? = null
    private var lastFetchTime: Long = 0

    // 检查是否有位置权限
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // 获取缓存的位置（非阻塞）
    fun getCachedLocation(): LocationData? {
        val now = System.currentTimeMillis()
        if (cachedLocation != null && now - lastFetchTime < CACHE_DURATION_MS) {
            return cachedLocation
        }
        // 缓存过期，异步刷新
        refreshLocation()
        return cachedLocation
    }

    // 异步刷新位置
    fun refreshLocation() {
        if (!hasPermission()) {
            Log.w(TAG, "No location permission")
            return
        }
        try {
            // 先读上次已知位置（即时返回）
            client.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    cachedLocation = LocationData(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        timestamp = location.time
                    )
                    lastFetchTime = System.currentTimeMillis()
                    Log.d(TAG, "Location updated: ${location.latitude}, ${location.longitude} (accuracy: ${location.accuracy}m)")
                }
            }
            // 同时请求一次新位置（低功耗）
            val cts = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_LOW_POWER, cts.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        cachedLocation = LocationData(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracy = location.accuracy,
                            timestamp = location.time
                        )
                        lastFetchTime = System.currentTimeMillis()
                        Log.d(TAG, "Fresh location: ${location.latitude}, ${location.longitude}")
                    }
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission revoked: ${e.message}")
        }
    }

    // 计算两点间距离（米）— Haversine 公式
    fun distanceTo(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // 地球半径（米）
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
