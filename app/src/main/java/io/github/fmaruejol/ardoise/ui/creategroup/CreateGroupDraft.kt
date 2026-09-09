package io.github.fmaruejol.ardoise.ui.creategroup

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CreateGroupDraft(
    val name: String,
    val currencyCode: String,
    val currencyChosen: Boolean,
    val information: String,
    val participants: List<String>,
    val youIndex: Int,
) : Parcelable
