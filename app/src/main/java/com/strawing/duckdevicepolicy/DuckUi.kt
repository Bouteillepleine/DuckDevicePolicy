package com.strawing.duckdevicepolicy

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.R as MR

enum class Health { GOOD, PAUSED, BAD }

class DuckUi(private val activity: Activity) {

    lateinit var content: LinearLayout
        private set
    private lateinit var headerHolder: LinearLayout
    private lateinit var headerDivider: View
    private lateinit var scroll: ScrollView
    private lateinit var nav: BottomNavigationView

    val onSurface get() = attr(MR.attr.colorOnSurface)
    val onSurfaceVar get() = attr(MR.attr.colorOnSurfaceVariant)
    val surfaceCard get() = attr(MR.attr.colorSurfaceContainer, attr(MR.attr.colorSurface))
    val outline get() = attr(MR.attr.colorOutlineVariant)
    val primary get() = attr(MR.attr.colorPrimary)
    val primaryContainer get() = attr(MR.attr.colorPrimaryContainer)
    val onPrimaryContainer get() = attr(MR.attr.colorOnPrimaryContainer)
    val errorContainer get() = attr(MR.attr.colorErrorContainer)
    val onErrorContainer get() = attr(MR.attr.colorOnErrorContainer)

    fun scaffold(onTabSelected: (Int) -> Unit, startTab: Int): View {
        content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(24))
        }
        scroll = ScrollView(activity).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            )
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        headerHolder = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
        }
        headerDivider = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(outline)
            alpha = 0f
        }
        scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            headerDivider.alpha = if (scrollY > dp(4)) 1f else 0f
        }
        nav = BottomNavigationView(activity).apply {
            inflateMenu(R.menu.bottom_nav)
            selectedItemId = startTab
            setOnItemSelectedListener { item ->
                onTabSelected(item.itemId)
                scroll.scrollTo(0, 0)
                headerDivider.alpha = 0f
                true
            }
        }
        val shell = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(headerHolder)
            addView(headerDivider)
            addView(scroll)
            addView(nav)
        }
        ViewCompat.setOnApplyWindowInsetsListener(shell) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        return shell
    }

    fun setHeader(title: String, onThemeChanged: () -> Unit) {
        headerHolder.removeAllViews()
        headerHolder.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(14), dp(4), dp(10))
            addView(TextView(activity).apply {
                text = DUCK
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 27f)
                setPadding(0, 0, dp(10), 0)
            })
            addView(TextView(activity).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(onSurface)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams =
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(themeButton(onThemeChanged))
        })
    }

    private fun themeButton(onThemeChanged: () -> Unit): View {
        val mode = Theming.current(activity)
        return TextView(activity).apply {
            text = mode.icon
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            contentDescription = "Theme: ${mode.label}"
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(surfaceCard)
                setStroke(dp(1), outline)
            }
            isClickable = true
            setOnClickListener {
                val next = mode.next()
                Theming.apply(activity, next)
                Toast.makeText(activity, next.label, Toast.LENGTH_SHORT).show()
                onThemeChanged()
            }
        }
    }

    fun heroCard(
        title: String,
        detail: String,
        health: Health,
        switchLabel: String?,
        switchChecked: Boolean,
        switchEnabled: Boolean,
        onSwitch: (Boolean) -> Unit,
    ): View {
        val good = health != Health.BAD
        val bg = if (good) primaryContainer else errorContainer
        val fg = if (good) onPrimaryContainer else onErrorContainer
        val card = filledCard(bg, dp(22))
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(ImageView(activity).apply {
            setImageResource(if (health == Health.GOOD) R.drawable.ic_status else R.drawable.ic_shield)
            setColorFilter(fg)
            alpha = if (health == Health.PAUSED) 0.55f else 1f
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply {
                rightMargin = dp(16)
            }
        })
        row.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(activity).apply {
                text = title
                setTextColor(fg)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })
            addView(TextView(activity).apply {
                text = detail
                setTextColor(fg)
                alpha = 0.9f
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setPadding(0, dp(2), 0, 0)
            })
        })
        if (switchLabel != null) {
            row.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(10), 0, 0, 0)
                addView(MaterialSwitch(activity).apply {
                    isChecked = switchChecked
                    isEnabled = switchEnabled
                    contentDescription = switchLabel
                    setOnCheckedChangeListener { _, checked -> onSwitch(checked) }
                })
                addView(TextView(activity).apply {
                    text = switchLabel
                    setTextColor(fg)
                    alpha = 0.75f
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setPadding(0, dp(2), 0, 0)
                })
            })
        }
        card.addView(row)
        return card
    }

    fun column(padTop: Int = 14, padBottom: Int = 14): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(padTop), dp(16), dp(padBottom))
    }

    fun sectionLabel(text: String, trailing: View? = null): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(activity).apply {
            this.text = text.uppercase()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.09f
            setTextColor(onSurfaceVar)
            setPadding(dp(6), dp(20), 0, dp(8))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (trailing != null) addView(trailing)
    }

    fun textAction(label: String, onClick: () -> Unit): TextView = TextView(activity).apply {
        text = label
        setTextColor(primary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(10), dp(10), dp(10), dp(6))
        isClickable = true
        setOnClickListener { onClick() }
    }

    fun filledCard(background: Int, padding: Int): MaterialCardView =
        MaterialCardView(activity).apply {
            radius = dp(24).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(background)
            setContentPadding(padding, padding, padding, padding)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

    fun outlinedCard(): MaterialCardView = MaterialCardView(activity).apply {
        radius = dp(24).toFloat()
        cardElevation = 0f
        strokeWidth = dp(1)
        strokeColor = outline
        setCardBackgroundColor(surfaceCard)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(2) }
    }

    fun thinDivider(): View = View(activity).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            .apply { topMargin = dp(2) }
        setBackgroundColor(outline)
        alpha = 0.7f
    }

    fun chip(text: String, highlight: Boolean = false): TextView = TextView(activity).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        setTextColor(if (highlight) onErrorContainer else onSurface)
        typeface = Typeface.MONOSPACE
        setPadding(dp(10), dp(4), dp(10), dp(4))
        background = GradientDrawable().apply {
            cornerRadius = dp(9).toFloat()
            setColor(if (highlight) errorContainer else attr(MR.attr.colorSurfaceContainerHighest, surfaceCard))
        }
    }

    fun infoRow(label: String, value: String): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(7), 0, dp(7))
        addView(TextView(activity).apply {
            text = label
            setTextColor(onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(TextView(activity).apply {
            text = value
            setTextColor(onSurfaceVar)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            gravity = Gravity.END
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
    }

    fun statRow(label: String, value: String, highlight: Boolean = false): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            addView(TextView(activity).apply {
                text = label.replace("_", "_​")
                setTextColor(onSurface)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { rightMargin = dp(8) }
            })
            addView(chip(value, highlight))
        }

    fun noteRow(text: String): View = TextView(activity).apply {
        this.text = text
        setTextColor(onSurfaceVar)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(0, dp(10), 0, 0)
    }

    fun bodyText(text: String): View = TextView(activity).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(onSurfaceVar)
    }

    fun linkRow(label: String, onClick: () -> Unit): View = TextView(activity).apply {
        text = label
        setTextColor(primary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(12), 0, 0)
        isClickable = true
        setOnClickListener { onClick() }
    }

    fun footer(text: String): View = TextView(activity).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setTextColor(onSurfaceVar)
        gravity = Gravity.CENTER
        setPadding(0, dp(16), 0, 0)
    }

    fun toggleRow(
        icon: Int,
        title: String,
        subtitle: String,
        checked: Boolean,
        enabled: Boolean = true,
        onChange: (Boolean) -> Unit,
    ): View {
        val sw = MaterialSwitch(activity).apply {
            isChecked = checked
            isEnabled = enabled
            isClickable = false
            isFocusable = false
            contentDescription = title
            setPadding(dp(10), 0, 0, 0)
        }
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            isClickable = enabled
            alpha = if (enabled) 1f else 0.45f
            setOnClickListener {
                val value = !sw.isChecked
                sw.isChecked = value
                onChange(value)
            }
        }
        row.addView(ImageView(activity).apply {
            setImageResource(icon)
            setColorFilter(primary)
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                rightMargin = dp(14)
            }
        })
        row.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(activity).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(onSurface)
            })
            addView(TextView(activity).apply {
                text = subtitle
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setTextColor(onSurfaceVar)
                setPadding(0, dp(1), 0, 0)
            })
        })
        row.addView(sw)
        return row
    }

    fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    fun attr(attrId: Int, fallback: Int = Color.GRAY): Int =
        MaterialColors.getColor(activity.findViewById(android.R.id.content) ?: View(activity), attrId, fallback)

    companion object {
        private const val DUCK = "🦆"

        val TAB_STATUS = R.id.tab_status
        val TAB_CATEGORIES = R.id.tab_categories
        val TAB_DIAGNOSTICS = R.id.tab_diagnostics
    }
}
