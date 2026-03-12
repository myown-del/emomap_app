package com.example.emomap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory

object MapMarkerIconFactory {

    fun fromDrawableRes(context: Context, @DrawableRes drawableRes: Int): Icon? {
        return runCatching {
            val drawable = AppCompatResources.getDrawable(context, drawableRes) ?: return null

            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)

            IconFactory.getInstance(context).fromBitmap(bitmap)
        }.getOrElse { error ->
            Log.w("MapMarkerIconFactory", "Failed to build marker icon for res=$drawableRes", error)
            null
        }
    }
}
