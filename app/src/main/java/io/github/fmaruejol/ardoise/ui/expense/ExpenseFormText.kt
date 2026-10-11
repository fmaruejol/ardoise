package io.github.fmaruejol.ardoise.ui.expense

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.api.ExpenseLimits
import io.github.fmaruejol.ardoise.core.currency.format
import io.github.fmaruejol.ardoise.core.model.SplitMode
import io.github.fmaruejol.ardoise.ui.describe

/**
 * What the form and the split editor put into words: the split tile's summary,
 * a form error's message, and a stored percentage share as text.
 */
@Composable
internal fun ExpenseFormUiState.splitSummary(): String {
    val count = paidFor.size
    return when (splitMode) {
        SplitMode.EVENLY -> pluralStringResource(R.plurals.split_summary_evenly, count, count)
        SplitMode.BY_AMOUNT -> pluralStringResource(R.plurals.split_summary_amount, count, count)
        SplitMode.BY_SHARES -> pluralStringResource(R.plurals.split_summary_shares, count, count)
        SplitMode.BY_PERCENTAGE -> pluralStringResource(R.plurals.split_summary_percent, count, count)
    }
}

@Composable
internal fun ExpenseFormError.describe(state: ExpenseFormUiState): String = when (this) {
    ExpenseFormError.Title -> {
        val length = state.title.trim().length
        val allowed = ExpenseLimits.TITLE_LENGTH
        when {
            length == 0 -> {
                stringResource(R.string.expense_error_title)
            }

            length > allowed.last -> {
                pluralStringResource(R.plurals.expense_error_title_long, allowed.last, allowed.last)
            }

            else -> {
                pluralStringResource(R.plurals.expense_error_title_short, allowed.first, allowed.first)
            }
        }
    }

    ExpenseFormError.Amount -> {
        stringResource(R.string.expense_error_amount)
    }

    ExpenseFormError.Rate -> {
        stringResource(R.string.expense_error_rate)
    }

    ExpenseFormError.NobodyPaidFor -> {
        stringResource(R.string.expense_error_nobody)
    }

    ExpenseFormError.NegativeShare -> {
        if (state.splitMode == SplitMode.BY_AMOUNT && state.amount < 0) {
            stringResource(R.string.expense_error_income_by_amount)
        } else {
            stringResource(R.string.expense_error_negative_share)
        }
    }

    ExpenseFormError.Notes -> {
        pluralStringResource(
            R.plurals.expense_error_notes_long,
            ExpenseLimits.NOTES_MAX_LENGTH,
            ExpenseLimits.NOTES_MAX_LENGTH,
        )
    }

    is ExpenseFormError.SplitTotal -> {
        if (state.splitMode == SplitMode.BY_PERCENTAGE) {
            stringResource(R.string.expense_error_split_percent, percent(assigned))
        } else {
            stringResource(
                R.string.expense_error_split_amount,
                state.currency.format(assigned),
                state.currency.format(required),
            )
        }
    }

    is ExpenseFormError.Failed -> {
        error.describe()
    }
}

/** A stored percentage share back as text: `5000` reads as `50%`. */
internal fun percent(scaled: Long): String =
    java.math.BigDecimal(scaled)
        .divide(java.math.BigDecimal(SplitMode.BY_PERCENTAGE.shareScale))
        .stripTrailingZeros().toPlainString() + "%"
