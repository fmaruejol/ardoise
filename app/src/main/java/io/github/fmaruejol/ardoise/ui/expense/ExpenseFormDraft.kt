package io.github.fmaruejol.ardoise.ui.expense

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.LocalDate

@Parcelize
data class ExpenseFormDraft(
    val title: String,
    val amountText: String,
    val entryCurrencyCode: String?,
    val conversionRateText: String,
    val dateEpochDay: Long,
    val categoryId: Int,
    val paidById: String,
    val splitMode: String,
    val paidFor: Map<String, Long>,
    val splitText: Map<String, String>,
    val notes: String,
    val showNotes: Boolean,
    val recurrenceRule: String,
    val isReimbursement: Boolean,
) : Parcelable {
    val date: LocalDate get() = LocalDate.ofEpochDay(dateEpochDay)
}
