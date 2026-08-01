package com.elysium369.meet.ride.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface

object RideMapAvatarRenderer {
    fun render(
        context: Context,
        role: RideMarkerRole,
        selection: RideMapAvatarSelection,
        sizeDp: Int = 60,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (sizeDp * density).toInt().coerceAtLeast(sizeDp)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        when (role) {
            RideMarkerRole.DRIVER -> drawDriver(canvas, size.toFloat(), density, selection.driver)
            RideMarkerRole.PASSENGER_GPS -> drawPassenger(canvas, size.toFloat(), density, selection.passenger)
            else -> drawUtilityMarker(canvas, size.toFloat(), density, role)
        }
        return bitmap
    }

    private fun glowPaint(color: Int, density: Float, radius: Float = 5f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
            setShadowLayer(radius * density, 0f, 2f * density, Color.BLACK)
        }

    private fun drawDriver(canvas: Canvas, size: Float, density: Float, avatar: RideDriverAvatar) {
        when (avatar) {
            RideDriverAvatar.CRIMSON_DRAGON -> drawDragon(canvas, size, density)
            RideDriverAvatar.CYBER_WYVERN -> drawWyvern(canvas, size, density)
            RideDriverAvatar.OBSIDIAN_PHOENIX -> drawPhoenix(canvas, size, density)
            RideDriverAvatar.TURBO_RONIN -> drawRonin(canvas, size, density)
        }
    }

    private fun drawPassenger(canvas: Canvas, size: Float, density: Float, avatar: RidePassengerAvatar) {
        when (avatar) {
            RidePassengerAvatar.NEON_PERSON -> drawPerson(canvas, size, density)
            RidePassengerAvatar.CITY_EXPLORER -> drawExplorer(canvas, size, density)
            RidePassengerAvatar.AURA_HERO -> drawAuraHero(canvas, size, density)
            RidePassengerAvatar.VANGUARD_GUARDIAN -> drawGuardian(canvas, size, density)
        }
    }

    private fun drawDragon(canvas: Canvas, size: Float, density: Float) {
        val paint = glowPaint(Color.rgb(255, 23, 68), density, 7f)
        val path = Path().apply {
            moveTo(size * .50f, size * .08f)
            lineTo(size * .61f, size * .25f)
            lineTo(size * .86f, size * .15f)
            lineTo(size * .73f, size * .40f)
            lineTo(size * .91f, size * .49f)
            lineTo(size * .68f, size * .55f)
            cubicTo(size * .72f, size * .78f, size * .61f, size * .91f, size * .50f, size * .94f)
            cubicTo(size * .39f, size * .91f, size * .28f, size * .78f, size * .32f, size * .55f)
            lineTo(size * .09f, size * .49f)
            lineTo(size * .27f, size * .40f)
            lineTo(size * .14f, size * .15f)
            lineTo(size * .39f, size * .25f)
            close()
        }
        canvas.drawPath(path, paint)
        val eye = glowPaint(Color.rgb(255, 214, 0), density, 2f)
        canvas.drawCircle(size * .43f, size * .43f, size * .035f, eye)
        canvas.drawCircle(size * .57f, size * .43f, size * .035f, eye)
    }

    private fun drawWyvern(canvas: Canvas, size: Float, density: Float) {
        val wing = glowPaint(Color.rgb(0, 229, 255), density, 7f)
        val path = Path().apply {
            moveTo(size * .50f, size * .18f)
            cubicTo(size * .38f, size * .23f, size * .30f, size * .36f, size * .27f, size * .53f)
            lineTo(size * .07f, size * .26f)
            lineTo(size * .14f, size * .67f)
            lineTo(size * .36f, size * .58f)
            cubicTo(size * .35f, size * .77f, size * .42f, size * .89f, size * .50f, size * .94f)
            cubicTo(size * .58f, size * .89f, size * .65f, size * .77f, size * .64f, size * .58f)
            lineTo(size * .86f, size * .67f)
            lineTo(size * .93f, size * .26f)
            lineTo(size * .73f, size * .53f)
            cubicTo(size * .70f, size * .36f, size * .62f, size * .23f, size * .50f, size * .18f)
            close()
        }
        canvas.drawPath(path, wing)
        canvas.drawCircle(size * .50f, size * .43f, size * .14f, glowPaint(Color.rgb(20, 32, 58), density, 3f))
        canvas.drawCircle(size * .46f, size * .41f, size * .025f, glowPaint(Color.WHITE, density, 1f))
        canvas.drawCircle(size * .54f, size * .41f, size * .025f, glowPaint(Color.WHITE, density, 1f))
    }

    private fun drawPhoenix(canvas: Canvas, size: Float, density: Float) {
        val paint = glowPaint(Color.rgb(255, 145, 0), density, 8f)
        val path = Path().apply {
            moveTo(size * .50f, size * .06f)
            lineTo(size * .58f, size * .31f)
            lineTo(size * .84f, size * .13f)
            lineTo(size * .70f, size * .45f)
            lineTo(size * .95f, size * .48f)
            lineTo(size * .68f, size * .62f)
            lineTo(size * .74f, size * .90f)
            lineTo(size * .50f, size * .72f)
            lineTo(size * .26f, size * .90f)
            lineTo(size * .32f, size * .62f)
            lineTo(size * .05f, size * .48f)
            lineTo(size * .30f, size * .45f)
            lineTo(size * .16f, size * .13f)
            lineTo(size * .42f, size * .31f)
            close()
        }
        canvas.drawPath(path, paint)
        canvas.drawCircle(size * .50f, size * .48f, size * .11f, glowPaint(Color.rgb(32, 35, 42), density, 2f))
    }

    private fun drawRonin(canvas: Canvas, size: Float, density: Float) {
        val red = glowPaint(Color.rgb(255, 45, 85), density, 7f)
        val dark = glowPaint(Color.rgb(18, 24, 38), density, 3f)
        canvas.drawCircle(size * .50f, size * .51f, size * .39f, red)
        val helmet = Path().apply {
            moveTo(size * .24f, size * .48f)
            cubicTo(size * .28f, size * .19f, size * .72f, size * .19f, size * .76f, size * .48f)
            lineTo(size * .68f, size * .80f)
            lineTo(size * .32f, size * .80f)
            close()
        }
        canvas.drawPath(helmet, dark)
        val visor = glowPaint(Color.rgb(0, 229, 255), density, 6f)
        canvas.drawRoundRect(size * .30f, size * .43f, size * .70f, size * .56f, size * .04f, size * .04f, visor)
        canvas.drawRect(size * .46f, size * .57f, size * .54f, size * .86f, red)
    }

    private fun drawPerson(canvas: Canvas, size: Float, density: Float) {
        val paint = glowPaint(Color.rgb(0, 229, 255), density, 7f)
        canvas.drawCircle(size * .50f, size * .25f, size * .13f, paint)
        drawHumanBody(canvas, size, paint)
    }

    private fun drawExplorer(canvas: Canvas, size: Float, density: Float) {
        val paint = glowPaint(Color.rgb(0, 200, 255), density, 7f)
        canvas.drawCircle(size * .50f, size * .25f, size * .14f, paint)
        drawHumanBody(canvas, size, paint)
        val visor = glowPaint(Color.rgb(255, 214, 0), density, 4f)
        canvas.drawRoundRect(size * .38f, size * .20f, size * .62f, size * .28f, size * .03f, size * .03f, visor)
        canvas.drawCircle(size * .28f, size * .55f, size * .08f, glowPaint(Color.rgb(149, 117, 205), density, 3f))
    }

    private fun drawAuraHero(canvas: Canvas, size: Float, density: Float) {
        val aura = glowPaint(Color.rgb(255, 214, 0), density, 9f)
        val auraPath = Path().apply {
            moveTo(size * .50f, size * .03f)
            lineTo(size * .59f, size * .18f)
            lineTo(size * .75f, size * .09f)
            lineTo(size * .72f, size * .30f)
            lineTo(size * .91f, size * .32f)
            lineTo(size * .77f, size * .48f)
            lineTo(size * .91f, size * .70f)
            lineTo(size * .69f, size * .68f)
            lineTo(size * .62f, size * .96f)
            lineTo(size * .50f, size * .80f)
            lineTo(size * .38f, size * .96f)
            lineTo(size * .31f, size * .68f)
            lineTo(size * .09f, size * .70f)
            lineTo(size * .23f, size * .48f)
            lineTo(size * .09f, size * .32f)
            lineTo(size * .28f, size * .30f)
            lineTo(size * .25f, size * .09f)
            lineTo(size * .41f, size * .18f)
            close()
        }
        canvas.drawPath(auraPath, aura)
        val body = glowPaint(Color.rgb(74, 20, 140), density, 4f)
        canvas.drawCircle(size * .50f, size * .28f, size * .12f, body)
        drawHumanBody(canvas, size, body)
    }

    private fun drawGuardian(canvas: Canvas, size: Float, density: Float) {
        val shield = glowPaint(Color.rgb(124, 77, 255), density, 8f)
        val path = Path().apply {
            moveTo(size * .50f, size * .07f)
            lineTo(size * .84f, size * .22f)
            lineTo(size * .78f, size * .70f)
            cubicTo(size * .70f, size * .85f, size * .58f, size * .93f, size * .50f, size * .97f)
            cubicTo(size * .42f, size * .93f, size * .30f, size * .85f, size * .22f, size * .70f)
            lineTo(size * .16f, size * .22f)
            close()
        }
        canvas.drawPath(path, shield)
        val face = glowPaint(Color.rgb(8, 18, 32), density, 3f)
        canvas.drawRoundRect(size * .30f, size * .28f, size * .70f, size * .64f, size * .08f, size * .08f, face)
        val eye = glowPaint(Color.rgb(0, 229, 255), density, 5f)
        canvas.drawRect(size * .36f, size * .42f, size * .64f, size * .50f, eye)
    }

    private fun drawHumanBody(canvas: Canvas, size: Float, paint: Paint) {
        val body = Path().apply {
            moveTo(size * .35f, size * .42f)
            cubicTo(size * .24f, size * .47f, size * .21f, size * .67f, size * .25f, size * .76f)
            lineTo(size * .39f, size * .76f)
            lineTo(size * .39f, size * .94f)
            lineTo(size * .48f, size * .94f)
            lineTo(size * .50f, size * .73f)
            lineTo(size * .52f, size * .94f)
            lineTo(size * .61f, size * .94f)
            lineTo(size * .61f, size * .76f)
            lineTo(size * .75f, size * .76f)
            cubicTo(size * .79f, size * .67f, size * .76f, size * .47f, size * .65f, size * .42f)
            close()
        }
        canvas.drawPath(body, paint)
    }

    private fun drawUtilityMarker(canvas: Canvas, size: Float, density: Float, role: RideMarkerRole) {
        val color = when (role) {
            RideMarkerRole.PICKUP -> Color.rgb(0, 200, 83)
            RideMarkerRole.STOP -> Color.rgb(255, 214, 0)
            RideMarkerRole.DESTINATION -> Color.rgb(213, 0, 249)
            RideMarkerRole.ROAD_INCIDENT -> Color.rgb(255, 45, 85)
            RideMarkerRole.DRIVER -> Color.rgb(255, 23, 68)
            RideMarkerRole.PASSENGER_GPS -> Color.rgb(0, 188, 212)
        }
        val label = when (role) {
            RideMarkerRole.PICKUP -> "R"
            RideMarkerRole.STOP -> "P"
            RideMarkerRole.DESTINATION -> "D"
            RideMarkerRole.ROAD_INCIDENT -> "!"
            RideMarkerRole.DRIVER -> "C"
            RideMarkerRole.PASSENGER_GPS -> "U"
        }
        canvas.drawCircle(size / 2f, size / 2f, size * .38f, glowPaint(color, density, 6f))
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = size * .34f
            typeface = Typeface.DEFAULT_BOLD
        }
        val baseline = size / 2f - (text.ascent() + text.descent()) / 2f
        canvas.drawText(label, size / 2f, baseline, text)
    }
}
