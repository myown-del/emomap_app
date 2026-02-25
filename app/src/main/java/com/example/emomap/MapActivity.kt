package com.example.emomap

import android.app.DatePickerDialog
import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.emomap.databinding.ActivityMapBinding
import kotlinx.coroutines.launch
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MapActivity : BaseActivity() {
    
    private lateinit var binding: ActivityMapBinding
    private lateinit var authRepository: AuthRepository
    private lateinit var mapView: MapView
    private lateinit var mapController: IMapController
    private var selectedDate: String? = null
    private val emotionMarkers = mutableListOf<Marker>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize OSMDroid configuration
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        
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
        setupMap()
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
        
        // Filter button
        binding.btnFilter.setOnClickListener {
            loadEmotions()
        }
        
        // Chip group listener - simpler and more reliable with single selection
        binding.chipGroupEmotions.setOnCheckedStateChangeListener { _, checkedIds ->
            android.util.Log.d("MapActivity", "Chip selection changed: $checkedIds")
            loadEmotions()
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
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }
    
    private fun setupMap() {
        mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapController = mapView.controller
        
        // Set initial position (Moscow)
        val startPoint = GeoPoint(55.7558, 37.6176)
        mapController.setZoom(12.0)
        mapController.setCenter(startPoint)
        
        // Enable zoom controls
        mapView.setBuiltInZoomControls(true)
        mapView.setMultiTouchControls(true)
        
        // Set map click listener to hide emotion details
        mapView.setOnTouchListener { _, _ ->
            hideEmotionDetails()
            false
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
                        else -> "Ошибка загрузки эмоций: ${response.code()}"
                    }
                    Toast.makeText(this@MapActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                setLoadingState(false)
                Toast.makeText(this@MapActivity, "Ошибка сети: ${e.message}", Toast.LENGTH_LONG).show()
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
        
        // Filter by emotion type based on selected chip
        val checkedChipId = binding.chipGroupEmotions.checkedChipId
        when (checkedChipId) {
            R.id.chipHappy -> {
                android.util.Log.d("MapActivity", "Filtering for happy emotions (rating >= 7)")
                filtered = filtered.filter { it.rating >= 7 }
            }
            R.id.chipNeutral -> {
                android.util.Log.d("MapActivity", "Filtering for neutral emotions (rating 5-6)")
                filtered = filtered.filter { it.rating in 5..6 }
            }
            R.id.chipSad -> {
                android.util.Log.d("MapActivity", "Filtering for sad emotions (rating <= 4)")
                filtered = filtered.filter { it.rating <= 4 }
            }
            R.id.chipAllEmotions -> {
                android.util.Log.d("MapActivity", "Showing all emotions (no rating filter)")
                // No additional filtering - show all emotions
            }
            else -> {
                android.util.Log.d("MapActivity", "No chip selected, showing all emotions")
                // Fallback: if no chip is selected, show all emotions
            }
        }
        
        android.util.Log.d("MapActivity", "Filtered ${filtered.size} emotions from ${emotions.size} total")
        return filtered
    }
    
    private fun displayEmotionsOnMap(emotions: List<EmotionResponse>) {
        // Clear existing markers
        mapView.overlays.removeAll(emotionMarkers)
        emotionMarkers.clear()
        
        emotions.forEach { emotion ->
            val position = GeoPoint(emotion.latitude, emotion.longitude)
            
            val marker = Marker(mapView)
            marker.position = position
            marker.title = "Настроение: ${emotion.rating}"
            
            // Set marker icon based on emotion rating
            val iconRes = when {
                emotion.rating >= 7 -> android.R.drawable.presence_online // Green circle
                emotion.rating <= 4 -> android.R.drawable.presence_busy // Red circle  
                else -> android.R.drawable.presence_away // Orange circle
            }
            marker.icon = resources.getDrawable(iconRes, theme)
            
            // Set click listener
            marker.setOnMarkerClickListener { _, _ ->
                showEmotionDetails(emotion)
                true
            }
            
            mapView.overlays.add(marker)
            emotionMarkers.add(marker)
        }
        
        // Refresh map
        mapView.invalidate()
        
        // Adjust zoom to show all markers if there are any
        if (emotions.isNotEmpty()) {
            val latitudes = emotions.map { it.latitude }
            val longitudes = emotions.map { it.longitude }
            
            val centerLat = (latitudes.minOrNull()!! + latitudes.maxOrNull()!!) / 2
            val centerLon = (longitudes.minOrNull()!! + longitudes.maxOrNull()!!) / 2
            
            mapController.setCenter(GeoPoint(centerLat, centerLon))
        }
    }
    
    private fun showEmotionDetails(emotion: EmotionResponse) {
        binding.cardEmotionDetails.visibility = View.VISIBLE
        
        // Get address from coordinates
        val geocoder = Geocoder(this, Locale.getDefault())
        var address = "Координаты: ${emotion.latitude}, ${emotion.longitude}"
        
        try {
            val addresses = geocoder.getFromLocation(emotion.latitude, emotion.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                address = addr.getAddressLine(0) ?: address
            }
        } catch (e: IOException) {
            // Use coordinates as fallback
        }
        
        binding.tvEmotionLocation.text = address
        binding.tvEmotionRating.text = "Настроение: ${emotion.rating}/10"
        
        // Format date and time
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val displayFormat = SimpleDateFormat("dd.MM.yyyy в HH:mm", Locale.getDefault())
        try {
            val date = dateFormat.parse(emotion.createdAt ?: "")
            binding.tvEmotionDate.text = "Дата: ${displayFormat.format(date ?: "")}"
        } catch (e: Exception) {
            binding.tvEmotionDate.text = "Дата: ${emotion.createdAt ?: ""}"
        }
        
        // Improved comment display
        if (emotion.comment.isNullOrBlank()) {
            binding.tvEmotionComment.visibility = View.GONE
        } else {
            binding.tvEmotionComment.visibility = View.VISIBLE
            binding.tvEmotionComment.text = "💬 ${emotion.comment}"
        }
        
        android.util.Log.d("MapActivity", "Showing emotion details: rating=${emotion.rating}, comment='${emotion.comment}', location=${emotion.latitude},${emotion.longitude}")
    }
    
    private fun hideEmotionDetails() {
        binding.cardEmotionDetails.visibility = View.GONE
    }
    
    private fun setLoadingState(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnFilter.isEnabled = !isLoading
    }
    
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }
    
    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
} 
