package com.example.emomap

import android.content.Intent
import android.graphics.Color
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.emomap.databinding.ActivityHistoryBinding
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : BaseActivity() {
    
    private lateinit var binding: ActivityHistoryBinding
    private lateinit var authRepository: AuthRepository
    private lateinit var emotionAdapter: EmotionHistoryAdapter
    private val emotions = mutableListOf<EmotionResponse>()
    private var currentPeriod = "week"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityHistoryBinding.inflate(layoutInflater)
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
        loadData()
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
        setupRecyclerView()
        setupPeriodFilters()
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_history
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_map -> {
                    startActivity(Intent(this, MapActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_history -> true
                else -> false
            }
        }
    }
    
    private fun setupRecyclerView() {
        emotionAdapter = EmotionHistoryAdapter(emotions)
        binding.recyclerViewEmotions.apply {
            adapter = emotionAdapter
            layoutManager = LinearLayoutManager(this@HistoryActivity)
        }
    }
    
    private fun setupPeriodFilters() {
        // Set initial state
        binding.chipWeek.isChecked = true
        
        // Use simple click listeners for more reliable behavior
        binding.chipWeek.setOnClickListener {
            // Always set the correct selection state
            binding.chipWeek.isChecked = true
            binding.chipMonth.isChecked = false
            binding.chipYear.isChecked = false
            
            currentPeriod = "week"
            android.util.Log.d("HistoryActivity", "Clicked week - switching to week period")
            loadStatistics()
        }
        
        binding.chipMonth.setOnClickListener {
            // Always set the correct selection state
            binding.chipWeek.isChecked = false
            binding.chipMonth.isChecked = true
            binding.chipYear.isChecked = false
            
            currentPeriod = "month"
            android.util.Log.d("HistoryActivity", "Clicked month - switching to month period")
            loadStatistics()
        }
        
        binding.chipYear.setOnClickListener {
            // Always set the correct selection state
            binding.chipWeek.isChecked = false
            binding.chipMonth.isChecked = false
            binding.chipYear.isChecked = true
            
            currentPeriod = "year"
            android.util.Log.d("HistoryActivity", "Clicked year - switching to year period")
            loadStatistics()
        }
    }
    
    private fun loadData() {
        loadRecentEmotions()
        loadStatistics()
    }
    
    private fun loadRecentEmotions() {
        lifecycleScope.launch {
            try {
                val response = NetworkConfig.apiService.getUserEmotions()
                
                if (response.isSuccessful) {
                    val allEmotions = response.body() ?: emptyList()
                    // Take only recent 5 emotions
                    emotions.clear()
                    emotions.addAll(allEmotions.takeLast(5).reversed())
                    emotionAdapter.notifyDataSetChanged()
                } else {
                    handleApiError(response.code())
                }
            } catch (e: Exception) {
                Toast.makeText(this@HistoryActivity, "Ошибка загрузки эмоций: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun loadStatistics() {
        binding.progressBarChart.visibility = View.VISIBLE
        binding.chartView.visibility = View.GONE
        binding.tvNoData.visibility = View.GONE
        
        lifecycleScope.launch {
            try {
                android.util.Log.d("HistoryActivity", "Loading statistics for period: $currentPeriod")
                val response = NetworkConfig.apiService.getEmotionStatistics(currentPeriod)
                
                binding.progressBarChart.visibility = View.GONE
                
                if (response.isSuccessful) {
                    val statistics = response.body()
                    android.util.Log.d("HistoryActivity", "Statistics response: $statistics")
                    
                    if (statistics != null && statistics.periods.isNotEmpty()) {
                        android.util.Log.d("HistoryActivity", "Found ${statistics.periods.size} periods")
                        // Log each period for debugging
                        statistics.periods.forEachIndexed { index, period ->
                            android.util.Log.d("HistoryActivity", "Period $index: label='${period.periodLabel}', rating=${period.averageRating}, count=${period.count}")
                        }
                        displayChart(statistics)
                        updateChartLabel(statistics.periodType)
                        binding.chartView.visibility = View.VISIBLE
                    } else {
                        android.util.Log.d("HistoryActivity", "No statistics data available from API")
                        binding.tvNoData.visibility = View.VISIBLE
                        binding.tvNoData.text = "Нет данных для отображения.\nДобавьте эмоции, чтобы увидеть статистику."
                        updateChartLabel(currentPeriod)
                    }
                } else {
                    android.util.Log.e("HistoryActivity", "Statistics API error: ${response.code()}")
                    val errorBody = response.errorBody()?.string()
                    android.util.Log.e("HistoryActivity", "Error body: $errorBody")
                    handleApiError(response.code())
                    binding.tvNoData.visibility = View.VISIBLE
                    binding.tvNoData.text = "Ошибка загрузки данных: ${response.code()}"
                    updateChartLabel(currentPeriod)
                }
            } catch (e: Exception) {
                android.util.Log.e("HistoryActivity", "Statistics loading exception", e)
                binding.progressBarChart.visibility = View.GONE
                binding.tvNoData.visibility = View.VISIBLE
                binding.tvNoData.text = "Ошибка загрузки статистики: ${e.message}"
                Toast.makeText(this@HistoryActivity, "Ошибка загрузки статистики: ${e.message}", Toast.LENGTH_LONG).show()
                updateChartLabel(currentPeriod)
            }
        }
    }
    
    private fun displayChart(statistics: EmotionStatisticsResponse) {
        android.util.Log.d("HistoryActivity", "Displaying chart with ${statistics.periods.size} periods")
        binding.chartView.removeAllViews()
        
        val maxRating = 10.0
        val chartHeight = 140 // Chart bars height
        val valueSpaceHeight = 40 // Extra space for value labels above bars (increased)
        val barWidth = 50
        val barSpacing = 20
        val labelWidth = 80
        
        statistics.periods.forEach { period ->
            android.util.Log.d("HistoryActivity", "Adding bar for period: '${period.periodLabel}', rating: ${period.averageRating}")
            
            // Main container for each bar and its label
            val barContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    labelWidth + 10,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 5
                    marginEnd = 5
                }
                gravity = android.view.Gravity.CENTER
                setPadding(5, 4, 5, 4)
            }
            
            // Chart area (fixed height container with space for labels)
            val chartArea = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    barWidth,
                    chartHeight + valueSpaceHeight // Add space for value labels
                )
            }
            
            // Calculate bar height
            val barHeight = maxOf(20, ((period.averageRating / maxRating) * chartHeight).toInt())
            
            // Value label positioned just above the bar
            val valueLabel = TextView(this).apply {
                text = String.format(Locale.getDefault(), "%.1f", period.averageRating)
                textSize = 9f
                setTextColor(getColor(R.color.text_primary))
                gravity = android.view.Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    barWidth,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                    bottomMargin = barHeight + 5 // Position just above the bar with 5px gap
                }
            }
            
            // Bar positioned at bottom of chart area
            val bar = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    barWidth,
                    barHeight
                ).apply {
                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                    bottomMargin = 0 // Position at bottom of the chart area
                }
                setBackgroundColor(getBarColor(period.averageRating))
            }
            
            // Add value and bar to chart area
            chartArea.addView(bar)
            chartArea.addView(valueLabel)
            
            // Period label (below chart area)
            val periodLabel = TextView(this).apply {
                text = if (period.periodLabel.isNotBlank()) {
                    period.periodLabel
                } else {
                    "Label"
                }
                android.util.Log.d("HistoryActivity", "Creating period label: '${text}'")
                textSize = 8f
                setTextColor(getColor(R.color.text_primary))
                gravity = android.view.Gravity.CENTER
                maxLines = 3
                layoutParams = LinearLayout.LayoutParams(
                    labelWidth,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 8
                }
                setBackgroundColor(getColor(android.R.color.transparent))
                setPadding(4, 4, 4, 4)
                setTextColor(getColor(R.color.text_primary))
                setSingleLine(false)
                includeFontPadding = true
            }
            
            // Add chart area and label to main container
            barContainer.addView(chartArea)
            barContainer.addView(periodLabel)
            
            // Add to main chart view
            binding.chartView.addView(barContainer)
        }
        
        android.util.Log.d("HistoryActivity", "Chart display completed, child count: ${binding.chartView.childCount}")
    }
    
    private fun getBarColor(rating: Double): Int {
        return when {
            rating >= 7.0 -> getColor(R.color.success_green)
            rating >= 5.0 -> getColor(R.color.warning_orange)
            else -> getColor(R.color.error_red)
        }
    }
    
    private fun handleApiError(code: Int) {
        when (code) {
            401 -> {
                authRepository.logoutSync()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            else -> {
                Toast.makeText(this, "Ошибка сервера: $code", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun updateChartLabel(periodType: String = currentPeriod) {
        val labelText = when (periodType) {
            "week" -> "Динамика настроения по дням"
            "month" -> "Динамика настроения по неделям"  
            "year" -> "Динамика настроения по месяцам"
            else -> "Динамика настроения"
        }
        binding.tvChartLabel.text = labelText
    }
}

class EmotionHistoryAdapter(private val emotions: List<EmotionResponse>) : 
    RecyclerView.Adapter<EmotionHistoryAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvEmotionCircle: TextView = view.findViewById(R.id.tvEmotionCircle)
        val tvEmotionDescription: TextView = view.findViewById(R.id.tvEmotionDescription)
        val tvEmotionDate: TextView = view.findViewById(R.id.tvEmotionDate)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_emotion_history, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val emotion = emotions[position]
        
        // Set emotion circle
        val (emoji, color) = when {
            emotion.rating >= 7 -> "😊" to R.color.success_green
            emotion.rating >= 5 -> "😐" to R.color.warning_orange
            else -> "☹️" to R.color.error_red
        }
        
        holder.tvEmotionCircle.text = emoji
        holder.tvEmotionCircle.backgroundTintList = 
            holder.itemView.context.getColorStateList(color)
        
        // Get location name
        val context = holder.itemView.context
        val geocoder = Geocoder(context, Locale.getDefault())
        var locationName = "Неизвестное место"
        
        try {
            val addresses = geocoder.getFromLocation(emotion.latitude, emotion.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                locationName = address.locality ?: address.subAdminArea ?: 
                    address.adminArea ?: "Координаты: ${emotion.latitude}, ${emotion.longitude}"
            }
        } catch (e: IOException) {
            // Use default name
        }
        
        holder.tvEmotionDescription.text = "Настроение: ${emotion.rating}, $locationName"
        
        // Format date
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val displayFormat = SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault())
        try {
            val date = dateFormat.parse(emotion.createdAt ?: "")
            holder.tvEmotionDate.text = displayFormat.format(date ?: "")
        } catch (e: Exception) {
            holder.tvEmotionDate.text = emotion.createdAt ?: ""
        }
    }
    
    override fun getItemCount() = emotions.size
} 
