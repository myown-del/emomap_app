package com.example.emomap

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.emomap.databinding.ActivityHistoryBinding
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

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
        val isCompact = statistics.periods.size > 8
        val chartHeight = if (isCompact) 132.dp() else 148.dp()
        val valueSpaceHeight = 30.dp()
        val barWidth = if (isCompact) 28.dp() else 36.dp()
        val labelWidth = if (isCompact) 52.dp() else 64.dp()
        val minBarHeight = 14.dp()
        val barCornerRadius = 10f.dp()
        val barTrackColor = ColorUtils.setAlphaComponent(getColor(R.color.surface_variant), 80)

        binding.chartView.setPadding(6.dp(), 4.dp(), 6.dp(), 2.dp())

        statistics.periods.forEachIndexed { index, period ->
            android.util.Log.d("HistoryActivity", "Adding bar for period: '${period.periodLabel}', rating: ${period.averageRating}")

            val barContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    labelWidth,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 4.dp()
                    marginEnd = 4.dp()
                }
                gravity = Gravity.CENTER_HORIZONTAL
            }

            val chartArea = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    labelWidth,
                    chartHeight + valueSpaceHeight
                )
            }

            val rating = period.averageRating.coerceIn(0.0, maxRating)
            val barHeight = maxOf(minBarHeight, ((rating / maxRating) * chartHeight).toInt())

            val barTrack = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    barWidth,
                    chartHeight
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = barCornerRadius
                    setColor(barTrackColor)
                }
            }

            val bar = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    barWidth,
                    barHeight
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadii = floatArrayOf(
                        barCornerRadius, barCornerRadius,
                        barCornerRadius, barCornerRadius,
                        4f.dp(), 4f.dp(),
                        4f.dp(), 4f.dp()
                    )
                    setColor(getBarColor(rating))
                }
            }

            val valueLabel = TextView(this).apply {
                text = String.format(Locale.getDefault(), "%.1f", rating)
                textSize = 11f
                setTextColor(getColor(R.color.text_primary))
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    labelWidth,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = barHeight + 6.dp()
                }
                includeFontPadding = false
            }

            chartArea.addView(barTrack)
            chartArea.addView(bar)
            chartArea.addView(valueLabel)

            val periodLabel = TextView(this).apply {
                text = formatPeriodLabel(period.periodLabel, index, statistics.periodType)
                android.util.Log.d("HistoryActivity", "Creating period label: '${text}'")
                textSize = 11f
                setTextColor(getColor(R.color.text_secondary))
                gravity = Gravity.CENTER
                maxLines = if (statistics.periodType == "year") 2 else 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    labelWidth,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 10.dp()
                }
                includeFontPadding = false
            }

            barContainer.addView(chartArea)
            barContainer.addView(periodLabel)

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

    private fun formatPeriodLabel(periodLabel: String, index: Int, periodType: String): String {
        return when (periodType) {
            "week" -> formatDayLabel(periodLabel, index)
            "month" -> formatWeekLabel(periodLabel, index)
            else -> periodLabel.ifBlank { (index + 1).toString() }
        }
    }

    private fun formatDayLabel(periodLabel: String, index: Int): String {
        val normalized = periodLabel.trim().lowercase(Locale.getDefault())
        val dayMap = mapOf(
            "monday" to "Пн",
            "mon" to "Пн",
            "понедельник" to "Пн",
            "пн" to "Пн",
            "tuesday" to "Вт",
            "tue" to "Вт",
            "tues" to "Вт",
            "вторник" to "Вт",
            "вт" to "Вт",
            "wednesday" to "Ср",
            "wed" to "Ср",
            "среда" to "Ср",
            "ср" to "Ср",
            "thursday" to "Чт",
            "thu" to "Чт",
            "thur" to "Чт",
            "thurs" to "Чт",
            "четверг" to "Чт",
            "чт" to "Чт",
            "friday" to "Пт",
            "fri" to "Пт",
            "пятница" to "Пт",
            "пт" to "Пт",
            "saturday" to "Сб",
            "sat" to "Сб",
            "суббота" to "Сб",
            "сб" to "Сб",
            "sunday" to "Вс",
            "sun" to "Вс",
            "воскресенье" to "Вс",
            "вс" to "Вс"
        )
        dayMap[normalized]?.let { return it }

        if (periodLabel.isNotBlank() && periodLabel.length <= 3) {
            return periodLabel.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }

        val fallback = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        return fallback[index % fallback.size]
    }

    private fun formatWeekLabel(periodLabel: String, index: Int): String {
        if (periodLabel.isBlank()) {
            return "Нед ${index + 1}"
        }

        val number = Regex("""\d+""").find(periodLabel)?.value
        if (number != null) {
            return "Нед $number"
        }

        val normalized = periodLabel.trim().lowercase(Locale.getDefault())
        if (normalized.contains("week") || normalized.contains("нед")) {
            return "Нед ${index + 1}"
        }

        return periodLabel
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()

    private fun Float.dp(): Float = this * resources.displayMetrics.density
    
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
    private val geocoderLocale = Locale("ru", "RU")
    
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
        
        // Get full address from coordinates
        val context = holder.itemView.context
        val geocoder = Geocoder(context, geocoderLocale)
        var locationName = "Неизвестное место"
        
        try {
            val addresses = geocoder.getFromLocation(emotion.latitude, emotion.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                locationName = address.getAddressLine(0) ?: locationName
            }
        } catch (e: IOException) {
            // Keep default fallback
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
