package com.jesse.finly.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withTranslation
import kotlin.random.Random

class ConfettiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Particle(
        var x: Float,
        var y: Float,
        var size: Float,
        var speedY: Float,
        var speedX: Float,
        var rotation: Float,
        var rotationSpeed: Float,
        var color: Int,
        var shape: Int
    )

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null

    private val colors = intArrayOf(
        "#4CAF50".toColorInt(),
        "#FFD700".toColorInt(),
        "#2196F3".toColorInt(),
        "#9C27B0".toColorInt(),
        "#FF5722".toColorInt(),
        "#E91E63".toColorInt()
    )

    init {
        isClickable = false
        isFocusable = false
    }

    fun dispararConfetes() {
        val w = width.coerceAtLeast(500).toFloat()
        particles.clear()

        repeat(80) {
            particles.add(
                Particle(
                    x = Random.nextFloat() * w,
                    y = Random.nextFloat() * -300f - 50f,
                    size = Random.nextFloat() * 12f + 10f,
                    speedY = Random.nextFloat() * 16f + 10f,
                    speedX = Random.nextFloat() * 6f - 3f,
                    rotation = Random.nextFloat() * 360f,
                    rotationSpeed = Random.nextFloat() * 10f - 5f,
                    color = colors[Random.nextInt(colors.size)],
                    shape = Random.nextInt(2)
                )
            )
        }

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3200L
            interpolator = LinearInterpolator()
            addUpdateListener {
                particles.forEach { p ->
                    p.y += p.speedY
                    p.x += p.speedX
                    p.rotation += p.rotationSpeed
                }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    limpar()
                }
            })
            start()
        }
    }

    fun limpar() {
        animator?.cancel()
        particles.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (particles.isEmpty()) return

        particles.forEach { p ->
            paint.color = p.color
            canvas.withTranslation(p.x, p.y) {
                rotate(p.rotation)
                if (p.shape == 0) {
                    drawRect(-p.size / 2, -p.size / 2, p.size / 2, p.size / 2, paint)
                } else {
                    drawCircle(0f, 0f, p.size / 2, paint)
                }
            }
        }
    }
}
