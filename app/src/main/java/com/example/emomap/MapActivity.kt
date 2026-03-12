package com.example.emomap

import android.app.DatePickerDialog
import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.emomap.databinding.ActivityMapBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MapActivity : BaseActivity() {

    private lateinit var binding: ActivityMapBinding
    private lateinit var authRepository: AuthRepository
    private lateinit var mapView: MapView
    private var maplibreMap: MapLibreMap? = null
    private var selectedDate: String? = null
    private var isStyleLoaded = false
    private var pendingEmotions: List<EmotionResponse> = emptyList()
    private val emotionMarkers = mutableListOf<Marker>()
    private val emotionByMarkerId = mutableMapOf<Long, EmotionResponse>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authRepository = AuthRepository(this)

        // Check if user is logged in
        if (!authRepository.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setupToolbar()
        setupUI()
        setupMap(savedInstanceState)
    }

    override fun setupToolbar() {
        setSupportActionBar(binding.topBar.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun setupUI() {
        binding.topBar.btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        setupBottomNavigation()
        setupFilterControls()
        loadEmotions()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_map
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }

                R.id.nav_map -> true
                R.id.nav_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    finish()
                    true
                }

                else -> false
            }
        }
    }

    private fun setupFilterControls() {
        // Date picker
        binding.etDateFilter.setOnClickListener {
            showDatePicker()
        }

        // Reset button
        binding.btnFilter.setOnClickListener {
            selectedDate = null
            binding.etDateFilter.text?.clear()
            clearEmotionFilterSelection()
            loadEmotions()
        }

        setupEmotionFilterButtons()
    }

    private fun setupEmotionFilterButtons() {
        val buttons = emotionFilterButtons()
        buttons.forEach { button ->
            button.setOnClickListener {
                if (button.isChecked) {
                    buttons.filter { it != button }.forEach { it.isChecked = false }
                }
                loadEmotions()
            }
        }
    }

    private fun emotionFilterButtons(): List<MaterialButton> {
        return listOf(binding.chipSad, binding.chipNeutral, binding.chipHappy)
    }

    private fun clearEmotionFilterSelection() {
        emotionFilterButtons().forEach { it.isChecked = false }
    }

    private fun getCheckedEmotionFilterId(): Int {
        return when {
            binding.chipHappy.isChecked -> R.id.chipHappy
            binding.chipNeutral.isChecked -> R.id.chipNeutral
            binding.chipSad.isChecked -> R.id.chipSad
            else -> View.NO_ID
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                selectedDate = format.format(calendar.time)
                val displayFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                binding.etDateFilter.setText(displayFormat.format(calendar.time))
                loadEmotions()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun setupMap(savedInstanceState: Bundle?) {
        mapView = binding.mapView
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            maplibreMap = map
            map.setStyle(Style.Builder().fromJson(MapConfig.DEFAULT_STYLE_JSON)) {
                isStyleLoaded = true

                val startPoint = LatLng(55.7558, 37.6176)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(startPoint, 12.0))

                map.addOnMapClickListener {
                    hideEmotionDetails()
                    false
                }

                map.setOnMarkerClickListener { marker ->
                    val emotion = emotionByMarkerId[marker.id] ?: return@setOnMarkerClickListener false
                    showEmotionDetails(emotion)
                    true
                }

                displayEmotionsOnMap(pendingEmotions)
            }
        }
    }

    private fun loadEmotions() {
        setLoadingState(true)

        lifecycleScope.launch {
            try {
                val response = NetworkConfig.apiService.getUserEmotions()

                setLoadingState(false)

                if (response.isSuccessful) {
                    val emotions = response.body() ?: emptyList()
                    displayEmotionsOnMap(filterEmotions(emotions))
                } else {
                    val errorMessage = when (response.code()) {
                        401 -> {
                            authRepository.logout()
                            startActivity(Intent(this@MapActivity, LoginActivity::class.java))
                            finish()
                            return@launch
                        }

                        else -> "Failed to load emotions: ${response.code()}"
                    }
                    Toast.makeText(this@MapActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                setLoadingState(false)
                Toast.makeText(this@MapActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun filterEmotions(emotions: List<EmotionResponse>): List<EmotionResponse> {
        var filtered = emotions

        // Filter by date
        selectedDate?.let { date ->
            filtered = filtered.filter { emotion ->
                emotion.createdAt?.startsWith(date) == true
            }
        }

        // Filter by emotion type based on selected button
        val checkedChipId = getCheckedEmotionFilterId()
        when (checkedChipId) {
            R.id.chipHappy -> {
                android.util.Log.d("MapActivity", "Filtering for happy emotions (rating 7-10)")
                filtered = filtered.filter { it.rating in 7..10 }
            }

            R.id.chipNeutral -> {
                android.util.Log.d("MapActivity", "Filtering for neutral emotions (rating 4-6)")
                filtered = filtered.filter { it.rating in 4..6 }
            }

            R.id.chipSad -> {
                android.util.Log.d("MapActivity", "Filtering for sad emotions (rating 1-3)")
                filtered = filtered.filter { it.rating in 1..3 }
            }

            else -> {
                android.util.Log.d("MapActivity", "No chip selected, showing all emotions")
            }
        }

        android.util.Log.d("MapActivity", "Filtered ${filtered.size} emotions from ${emotions.size} total")
        return filtered
    }

    private fun displayEmotionsOnMap(emotions: List<EmotionResponse>) {
        pendingEmotions = emotions

        val map = maplibreMap ?: return
        if (!isStyleLoaded) return

        clearEmotionMarkers()

        if (emotions.isEmpty()) {
            return
        }

        emotions.forEach { emotion ->
            val iconRes = when {
                emotion.rating in 7..10 -> R.drawable.emotion_marker_happy
                emotion.rating in 1..3 -> R.drawable.emotion_marker_sad
                else -> R.drawable.emotion_marker_neutral
            }

            val markerOptions = MarkerOptions()
                .position(LatLng(emotion.latitude, emotion.longitude))
                .title("Mood: ${emotion.rating}")

            MapMarkerIconFactory.fromDrawableRes(this, iconRes)
                ?.let { markerOptions.icon(it) }

            val marker = map.addMarker(markerOptions)
            emotionMarkers.add(marker)
            emotionByMarkerId[marker.id] = emotion
        }

        if (emotions.size == 1) {
            val point = LatLng(emotions.first().latitude, emotions.first().longitude)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(point, 14.0))
            return
        }

        val boundsBuilder = LatLngBounds.Builder()
        emotions.forEach { emotion ->
            boundsBuilder.include(LatLng(emotion.latitude, emotion.longitude))
        }
        val bounds = boundsBuilder.build()
        mapView.post {
            try {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120), 700)
            } catch (_: Exception) {
                val latitudes = emotions.map { it.latitude }
                val longitudes = emotions.map { it.longitude }
                val centerLat = (latitudes.minOrNull()!! + latitudes.maxOrNull()!!) / 2
                val centerLon = (longitudes.minOrNull()!! + longitudes.maxOrNull()!!) / 2
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(centerLat, centerLon), 12.0))
            }
        }
    }

    private fun clearEmotionMarkers() {
        emotionMarkers.forEach { it.remove() }
        emotionMarkers.clear()
        emotionByMarkerId.clear()
    }

    private fun showEmotionDetails(emotion: EmotionResponse) {
        binding.cardEmotionDetails.visibility = View.VISIBLE

        val geocoder = Geocoder(this, Locale.getDefault())
        var address = "Coordinates: ${emotion.latitude}, ${emotion.longitude}"

        try {
            val addresses = geocoder.getFromLocation(emotion.latitude, emotion.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                address = addr.getAddressLine(0) ?: address
            }
        } catch (_: IOException) {
            // Keep coordinate fallback.
        }

        binding.tvEmotionLocation.text = address
        binding.tvEmotionRating.text = "Mood: ${emotion.rating}/10"

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val displayFormat = SimpleDateFormat("dd.MM.yyyy 'at' HH:mm", Locale.getDefault())
        try {
            val date = dateFormat.parse(emotion.createdAt ?: "")
            binding.tvEmotionDate.text = "Date: ${displayFormat.format(date ?: "")}"
        } catch (_: Exception) {
            binding.tvEmotionDate.text = "Date: ${emotion.createdAt ?: ""}"
        }

        if (emotion.comment.isNullOrBlank()) {
            binding.tvEmotionComment.visibility = View.GONE
        } else {
            binding.tvEmotionComment.visibility = View.VISIBLE
            binding.tvEmotionComment.text = "Comment: ${emotion.comment}"
        }

        android.util.Log.d(
            "MapActivity",
            "Showing emotion details: rating=${emotion.rating}, comment='${emotion.comment}', location=${emotion.latitude},${emotion.longitude}"
        )
    }

    private fun hideEmotionDetails() {
        binding.cardEmotionDetails.visibility = View.GONE
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnFilter.isEnabled = !isLoading
    }

    override fun onStart() {
        super.onStart()
        if (::mapView.isInitialized) {
            mapView.onStart()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) {
            mapView.onResume()
        }
    }

    override fun onPause() {
        if (::mapView.isInitialized) {
            mapView.onPause()
        }
        super.onPause()
    }

    override fun onStop() {
        if (::mapView.isInitialized) {
            mapView.onStop()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (::mapView.isInitialized) {
            mapView.onDestroy()
        }
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::mapView.isInitialized) {
            mapView.onLowMemory()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::mapView.isInitialized) {
            mapView.onSaveInstanceState(outState)
        }
    }
}







