package org.odk.collect.android.formhierarchy

import android.content.Context
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import org.odk.collect.android.databinding.HierarchyElementBinding
import org.odk.collect.android.utilities.HtmlUtils

class HierarchyListItemView(context: Context) : ConstraintLayout(context) {

    val binding = HierarchyElementBinding.inflate(LayoutInflater.from(context), this, true)

    fun setElement(element: HierarchyElement) {
        val icon = element.icon
        if (icon != null) {
            binding.icon.visibility = VISIBLE
            binding.icon.setImageDrawable(icon)
        } else {
            binding.icon.visibility = GONE
        }

        binding.primaryText.text = element.primaryText

        val secondaryText = element.secondaryText
        if (secondaryText != null && secondaryText.isNotEmpty()) {
            binding.secondaryText.visibility = VISIBLE
            binding.secondaryText.text = HtmlUtils.textToHtml(secondaryText)
        } else {
            binding.secondaryText.visibility = GONE
        }
    }
}
