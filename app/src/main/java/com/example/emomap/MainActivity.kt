package com.example.emomap

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.Menu
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.emomap.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity : BaseActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var authRepository: AuthRepository
    private lateinit var locationManager: LocationManager
    private lateinit var mapView: MapView
    private lateinit var mapController: IMapController
    private var selectedLocation: GeoPoint? = null
    private var currentRating = 5

    private val mapPickerRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@registerForActivityResult
        if (result.resultCode != RESULT_OK) return@registerForActivityResult

        val latitude = data.getDoubleExtra(MapPickerActivity.EXTRA_LATITUDE, Double.NaN)
        val longitude = data.getDoubleExtra(MapPickerActivity.EXTRA_LONGITUDE, Double.NaN)
        if (latitude.isNaN() || longitude.isNaN()) return@registerForActivityResult

        val chosenPoint = GeoPoint(latitude, longitude)
        selectedLocation = chosenPoint
        mapController.setCenter(chosenPoint)
        mapController.setZoom(15.0)
        addMarker(chosenPoint)
        binding.layoutLocationOverlay.visibility = View.GONE
    }
    
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                getCurrentLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                getCurrentLocation()
            }
            else -> {
                Toast.makeText(this, getString(R.string.location_permission_denied), Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize OSMDroid configuration
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        authRepository = AuthRepository(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        // Check if user is logged in
        if (!authRepository.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        
        setupToolbar()
        setupUI()
        setupMapView()
    }
    
    override fun setupToolbar() {
        setSupportActionBar(binding.topBar.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean = false
    
    private fun setupUI() {
        binding.topBar.btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        setupEmotionFaces()
        setupRatingSlider()
        setupMapPickerEntry()
        setupLocationButton()
        setupSaveButton()
        setupBottomNavigation()
        updateEmotionFaceBasedOnRating(currentRating)
    }

    private fun setupMapPickerEntry() {
        binding.mapPreviewContainer.setOnClickListener {
            openMapPicker()
        }
    }

    private fun openMapPicker() {
        val intent = Intent(this, MapPickerActivity::class.java).apply {
            selectedLocation?.let { point ->
                putExtra(MapPickerActivity.EXTRA_LATITUDE, point.latitude)
                putExtra(MapPickerActivity.EXTRA_LONGITUDE, point.longitude)
            }
        }
        mapPickerRequest.launch(intent)
    }
    
    private fun setupEmotionFaces() {
        binding.layoutSadFace.setOnClickListener {
            selectEmotion(1, binding.tvSadFace)
        }
        
        binding.layoutNeutralFace.setOnClickListener {
            selectEmotion(5, binding.tvNeutralFace)
        }
        
        binding.layoutHappyFace.setOnClickListener {
            selectEmotion(10, binding.tvHappyFace)
        }
    }
    
    private fun selectEmotion(rating: Int, selectedView: View) {
        currentRating = rating
        binding.seekBarRating.progress = rating
        binding.tvRatingValue.text = rating.toString()
        
        // Reset all face backgrounds
        resetEmotionFaces()
        
        // Highlight selected face
        selectedView.alpha = 1.0f
        selectedView.scaleX = 1.1f
        selectedView.scaleY = 1.1f
    }
    
    private fun resetEmotionFaces() {
        listOf(binding.tvSadFace, binding.tvNeutralFace, binding.tvHappyFace).forEach { face ->
            face.alpha = 0.7f
            face.scaleX = 1.0f
            face.scaleY = 1.0f
        }
    }
    
    private fun setupRatingSlider() {
        binding.seekBarRating.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentRating = if (progress < 1) 1 else progress
                    binding.tvRatingValue.text = currentRating.toString()
                    updateEmotionFaceBasedOnRating(currentRating)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    private fun updateEmotionFaceBasedOnRating(rating: Int) {
        resetEmotionFaces()
        
        when {
            rating <= 3 -> {
                binding.tvSadFace.alpha = 1.0f
                binding.tvSadFace.scaleX = 1.1f
                binding.tvSadFace.scaleY = 1.1f
            }
            rating <= 7 -> {
                binding.tvNeutralFace.alpha = 1.0f
                binding.tvNeutralFace.scaleX = 1.1f
                binding.tvNeutralFace.scaleY = 1.1f
            }
            else -> {
                binding.tvHappyFace.alpha = 1.0f
                binding.tvHappyFace.scaleX = 1.1f
                binding.tvHappyFace.scaleY = 1.1f
            }
        }
    }
    
    private fun setupLocationButton() {
        binding.btnChooseLocation.setOnClickListener {
            openMapPicker()
        }
    }
    
    private fun setupSaveButton() {
        binding.btnSaveEmotion.setOnClickListener {
            saveEmotion()
        }
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_home
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_map -> {
                    startActivity(Intent(this, MapActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupMapView() {
        mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapController = mapView.controller
        
        // Set initial position (Moscow)
        val startPoint = GeoPoint(55.7558, 37.6176)
        mapController.setZoom(10.0)
        mapController.setCenter(startPoint)
        
        // Preview-only mini map. Tap opens full-screen picker.
        mapView.setBuiltInZoomControls(false)
        mapView.setMultiTouchControls(false)
        mapView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                openMapPicker()
            }
            true
        }
    }
    
    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentLocation()
            }
            else -> {
                locationPermissionRequest.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
    }
    
    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        binding.btnChooseLocation.text = getString(R.string.getting_location)
        binding.btnChooseLocation.isEnabled = false
        
        try {
            val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            binding.btnChooseLocation.text = getString(R.string.choose_location)
            binding.btnChooseLocation.isEnabled = true
            
            if (lastKnownLocation != null) {
                val currentLatLng = GeoPoint(lastKnownLocation.latitude, lastKnownLocation.longitude)
                selectedLocation = currentLatLng
                
                mapController.setCenter(currentLatLng)
                mapController.setZoom(15.0)
                addMarker(currentLatLng)
                binding.layoutLocationOverlay.visibility = View.GONE
            } else {
                Toast.makeText(this, "Не удалось получить текущее местоположение", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            binding.btnChooseLocation.text = getString(R.string.choose_location)
            binding.btnChooseLocation.isEnabled = true
            Toast.makeText(this, "Ошибка получения местоположения", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun addMarker(point: GeoPoint) {
        // Clear existing markers
        mapView.overlays.clear()
        
        val marker = Marker(mapView)
        marker.position = point
        marker.title = "Выбранное место"
        marker.icon = resources.getDrawable(android.R.drawable.ic_menu_mylocation, theme)
        
        mapView.overlays.add(marker)
        mapView.invalidate()
    }
    
    private fun saveEmotion() {
        val location = selectedLocation
        if (location == null) {
            Toast.makeText(this, "Выберите местоположение на карте", Toast.LENGTH_SHORT).show()
            return
        }
        
        val comment = binding.etComment.text.toString().trim().takeIf { it.isNotEmpty() }
        
        val emotionCreate = EmotionCreate(
            latitude = location.latitude,
            longitude = location.longitude,
            rating = currentRating,
            comment = comment
        )
        
        setLoadingState(true)
        
        lifecycleScope.launch {
            try {
                val response = NetworkConfig.apiService.createEmotion(emotionCreate)
                
                setLoadingState(false)
                
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, getString(R.string.emotion_saved), Toast.LENGTH_SHORT).show()
                    clearForm()
                } else {
                    val errorMessage = when (response.code()) {
                        401 -> "Необходимо войти в систему"
                        422 -> "Неверные данные"
                        else -> "Ошибка сервера: ${response.code()}"
                    }
                    Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                setLoadingState(false)
                Toast.makeText(this@MainActivity, getString(R.string.error_saving_emotion), Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun clearForm() {
        binding.etComment.text?.clear()
        binding.seekBarRating.progress = 5
        currentRating = 5
        binding.tvRatingValue.text = "5"
        selectedLocation = null
        mapView.overlays.clear()
        mapView.invalidate()
        binding.layoutLocationOverlay.visibility = View.VISIBLE
        resetEmotionFaces()
        updateEmotionFaceBasedOnRating(5)
    }
    
    private fun setLoadingState(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSaveEmotion.isEnabled = !isLoading
        binding.btnChooseLocation.isEnabled = !isLoading
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
