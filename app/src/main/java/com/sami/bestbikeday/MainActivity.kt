package com.sami.bestbikeday

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.sami.bestbikeday.ui.theme.BestBikeDayTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

var lastKnownUserLocation: String? = null

fun saveLastKnownUserLocation(context: Context, location: String) {
    val prefs = context.getSharedPreferences("bestbikeday_prefs", Context.MODE_PRIVATE)
    prefs.edit().putString("last_location", location).apply()
}

fun loadLastKnownUserLocation(context: Context): String? {
    val prefs = context.getSharedPreferences("bestbikeday_prefs", Context.MODE_PRIVATE)
    return prefs.getString("last_location", null)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestLocationPermissionsIfNeeded()
        enableEdgeToEdge()
        setContent {
            MainActivityContent()
        }
    }

    private fun requestLocationPermissionsIfNeeded() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val notGranted = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted) {
            ActivityCompat.requestPermissions(this, permissions, 1001)
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BestBikeDayTheme {
        Greeting("Android")
    }
}

@Composable
fun MainActivityContent() {
    var darkTheme by remember { mutableStateOf(false) }
    val gradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFB2FEFA), Color(0xFF4CAF50), Color(0xFF2196F3)),
        startY = 0f,
        endY = 1200f,
        tileMode = TileMode.Clamp
    )
    BestBikeDayTheme(darkTheme = darkTheme) {
        Box(modifier = Modifier.fillMaxSize().background(gradient)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(gradient)
                        .padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Best Bike Day",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 30.sp),
                            fontWeight = FontWeight.Bold,
                            color = if (darkTheme) Color.White else Color(0xFF4CAF50),
                        )
                    }
                    IconButton(
                        onClick = { darkTheme = !darkTheme },
                        modifier = Modifier
                    ) {
                        Icon(
                            imageVector = if (darkTheme) Icons.Filled.Brightness7 else Icons.Filled.Brightness4,
                            contentDescription = if (darkTheme) "Light mode" else "Dark mode",
                            tint = if (darkTheme) Color.White else Color.Black
                        )
                    }
                }
                WeatherScreen(darkTheme = darkTheme, gradient = gradient, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun WeatherScreen(darkTheme: Boolean, gradient: Brush, modifier: Modifier = Modifier) {
    var forecast by remember { mutableStateOf<List<ForecastDay>?>(null) }
    var location by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var enableRefresh by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Load last known location from SharedPreferences on first composition
    LaunchedEffect(Unit) {
        lastKnownUserLocation = loadLastKnownUserLocation(context)
    }

    // Helper to request a single location update
    fun requestSingleLocationUpdate(onLocation: (String) -> Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                lastKnownUserLocation = "${location.latitude},${location.longitude}"
                saveLastKnownUserLocation(context, lastKnownUserLocation!!)
                onLocation(lastKnownUserLocation!!)
                locationManager.removeUpdates(this)
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try {
            val provider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider != null) {
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        } catch (e: SecurityException) {
            // Ignore, permissions not granted
        }
    }

    suspend fun refreshWeather() {
        isRefreshing = true
        isLoading = true
        val loc = getDeviceLocationOrLastUserLocation(context)
        if (loc == null) {
            error = "No location available. Please enable location services."
            isRefreshing = false
            isLoading = false
            enableRefresh = false
            return
        }
        location = loc
        val result = withContext(Dispatchers.IO) {
            WeatherRepository.get7DayForecast(loc)
        }
        if (result != null) {
            forecast = result.forecast.forecastday
            error = null
        } else {
            error = "Failed to load weather."
        }
        isRefreshing = false
        isLoading = false
        enableRefresh = true
    }

    suspend fun refreshWeatherWithLocationRequest() {
        val loc = getDeviceLocationOrLastUserLocation(context)
        if (loc == null) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            if (isLocationEnabled) {
                // Try to request a single update and refresh when received
                requestSingleLocationUpdate { newLoc ->
                    coroutineScope.launch {
                        refreshWeather()
                    }
                }
            }
        } else {
            refreshWeather()
        }
    }

    // Polling effect to detect when location is enabled
    LaunchedEffect(Unit) {
        var wasLocationEnabled = false
        while (true) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            if (isLocationEnabled && !wasLocationEnabled) {
                coroutineScope.launch {
                    refreshWeatherWithLocationRequest()
                }
            }
            wasLocationEnabled = isLocationEnabled
            delay(2000) // Check every 2 seconds
        }
    }

    fun bikeScore(day: Day): Int {
        val tempScore = when {
            day.avgTempC in 18f..24f -> 1.0
            day.avgTempC in 10f..30f -> 0.7
            else -> 0.3
        }
        val rainScore = 1.0 - (day.dailyChanceOfRain / 100.0)
        val windScore = when {
            day.maxWindKph < 15f -> 1.0
            day.maxWindKph < 30f -> 0.7
            else -> 0.3
        }
        val score = (tempScore * 0.4 + rainScore * 0.4 + windScore * 0.2) * 100
        return score.roundToInt().coerceIn(0, 100)
    }

    val green = Color(0xFF4CAF50)
    val lightGreen = Color(0xFFC8E6C9)
    val blue = Color(0xFF2196F3)
    val yellow = Color(0xFFFFEB3B)
    val orange = Color(0xFFFF9800)
    val pink = Color(0xFFE91E63)
    val cardColors = listOf(lightGreen, blue.copy(alpha = 0.08f), yellow.copy(alpha = 0.12f), orange.copy(alpha = 0.10f), pink.copy(alpha = 0.08f))

    Column(
        modifier = modifier.background(gradient),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SwipeRefresh(state = rememberSwipeRefreshState(isRefreshing), onRefresh = {
            if (enableRefresh) {
                coroutineScope.launch {
                    refreshWeatherWithLocationRequest()
                }
            }
        }) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = green)
                }
            } else if (forecast != null) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(forecast!!.withIndex().toList()) { (idx, day) ->
                        val score = bikeScore(day.day)
                        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val date = inputFormat.parse(day.date)
                        val cal = Calendar.getInstance()
                        cal.time = date!!
                        val dayOfWeek = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault())
                        val cardColor = cardColors[idx % cardColors.size]
                        val dayColor = when (idx % 7) {
                            0 -> blue
                            1 -> green
                            2 -> orange
                            3 -> pink
                            4 -> yellow
                            5 -> Color(0xFF00BCD4)
                            else -> Color(0xFF9C27B0)
                        }
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (darkTheme) Color.Black else Color.White
                            ),
                            elevation = CardDefaults.cardElevation(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .height(170.dp)
                                .drawBehind {
                                    val px = with(density) { 8.dp.toPx() }
                                    drawRect(
                                        color = if (darkTheme) Color.DarkGray else dayColor,
                                        size = Size(px, size.height)
                                    )
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Info Column
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Day and icon centered
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = dayOfWeek,
                                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = if (darkTheme) Color.White else dayColor,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Image(
                                            painter = rememberAsyncImagePainter(
                                                model = "https:" + day.day.condition.icon
                                            ),
                                            contentDescription = day.day.condition.text,
                                            modifier = Modifier.size(40.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    // Weather condition centered
                                    Text(
                                        text = day.day.condition.text,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                                        color = if (darkTheme) Color.White else Color.Black,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    // Temperatures centered
                                    Row(
                                        modifier = Modifier.align(Alignment.CenterHorizontally),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${day.day.maxTempC}°C",
                                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = if (darkTheme) yellow else orange
                                        )
                                        Text(
                                            text = " / ${day.day.minTempC}°C",
                                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                                            color = if (darkTheme) pink else pink
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    // Wind speed centered
                                    val windText = if (day.day.maxWindKph > 0f) "🌬️${day.day.maxWindKph} kph" else "🌬️N/A"
                                    Text(
                                        text = windText,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                                        color = if (darkTheme) blue else green,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
                                }
                                // Speedometer visual
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(90.dp).padding(start = 8.dp)
                                ) {
                                    SpeedometerGauge(score = score, color = when {
                                        score >= 80 -> if (darkTheme) yellow else green
                                        score >= 60 -> if (darkTheme) blue else blue
                                        score >= 40 -> if (darkTheme) orange else orange
                                        else -> if (darkTheme) pink else pink
                                    })
                                }
                            }
                        }
                    }
                }
            } else if (error != null) {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                if (lastKnownUserLocation != null && !isLocationEnabled) {
                    // Show a message that no weather data is available, pull to refresh
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "No weather data available. Pull to refresh.", color = Color.Gray)
                    }
                } else {
                    Text(text = error!!, color = Color.Red)
                }
            }
        }
    }
}

@Composable
fun SpeedometerGauge(score: Int, color: Color) {
    val density = LocalDensity.current
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
        Canvas(modifier = Modifier.size(80.dp)) {
            val stroke = with(density) { 12.dp.toPx() }
            // Draw background arc
            drawArc(
                color = Color.LightGray.copy(alpha = 0.3f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            // Draw score arc
            drawArc(
                color = color,
                startAngle = 135f,
                sweepAngle = 270f * (score / 100f),
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        // Speedometer icon in center
        Icon(
            imageVector = Icons.Filled.DirectionsBike,
            contentDescription = "Speedometer",
            tint = color,
            modifier = Modifier.size(36.dp)
        )
        // Percentage text below icon
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(44.dp))
            Text(
                text = "$score%",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@SuppressLint("MissingPermission")
fun getDeviceLocationOrLastUserLocation(context: Context): String? {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    if (isLocationEnabled &&
        (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
         ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
        val providers = locationManager.getProviders(true)
        var bestLocation: Location? = null
        for (provider in providers) {
            val l = locationManager.getLastKnownLocation(provider) ?: continue
            if (bestLocation == null || l.time > bestLocation.time) {
                bestLocation = l
            }
        }
        bestLocation?.let {
            lastKnownUserLocation = "${it.latitude},${it.longitude}"
            saveLastKnownUserLocation(context, lastKnownUserLocation!!)
            return lastKnownUserLocation
        }
    }
    // If location is off, do not update lastKnownUserLocation, just return its value
    return lastKnownUserLocation
}