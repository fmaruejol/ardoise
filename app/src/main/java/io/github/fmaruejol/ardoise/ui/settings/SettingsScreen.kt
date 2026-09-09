package io.github.fmaruejol.ardoise.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.data.AppLanguage
import io.github.fmaruejol.ardoise.data.AppTheme
import io.github.fmaruejol.ardoise.ui.components.SectionLabel
import io.github.fmaruejol.ardoise.ui.components.currencyLabel
import io.github.fmaruejol.ardoise.ui.host
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onServer: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SettingsScreen(
        state = state,
        onBack = onBack,
        onServer = onServer,
        onAbout = onAbout,
        onDefaultCurrencyChange = viewModel::onDefaultCurrencyChange,
        onLanguageChange = viewModel::onLanguageChange,
        onThemeChange = viewModel::onThemeChange,
        modifier = modifier,
    )
}

/**
 * App settings. Dynamic colour, split rounding, notifications and CSV export
 * are switches for things the app does not do yet, so they arrive with the
 * features they control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onServer: () -> Unit,
    onAbout: () -> Unit,
    onDefaultCurrencyChange: (String) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeChange: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel(
                text = stringResource(R.string.settings_appearance),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ThemeRow(theme = state.theme, onChange = onThemeChange)
            LanguageRow(language = state.language, onChange = onLanguageChange)

            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

            SectionLabel(
                text = stringResource(R.string.settings_defaults),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            DefaultCurrencyRow(
                code = state.defaultCurrencyCode,
                onChange = onDefaultCurrencyChange,
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

            SectionLabel(
                text = stringResource(R.string.settings_data),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            SettingsRow(
                title = stringResource(R.string.settings_server),
                subtitle = state.baseUrl.host(),
                icon = R.drawable.ic_dns,
                onClick = onServer,
            )
            SettingsRow(
                title = stringResource(R.string.settings_about),
                icon = R.drawable.ic_info,
                onClick = onAbout,
            )
        }
    }
}

/**
 * "Currency for new groups": the same `SUPPORTED_CODES` in the same order as
 * the create-group form. The row shows what is *stored*, read back through the
 * flow, so a failed write could not leave the screen claiming otherwise.
 */
@Composable
private fun DefaultCurrencyRow(code: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        SettingsRow(
            title = stringResource(R.string.settings_currency),
            subtitle = currencyLabel(code),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GroupCurrency.SUPPORTED_CODES.forEach { option ->
                DropdownMenuItem(
                    text = { Text(currencyLabel(option)) },
                    onClick = {
                        onChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * "Theme". Following the device is the default: a phone that switches at
 * sunset is asking every app to. See `ui/theme/Color.kt` for the schemes.
 */
@Composable
private fun ThemeRow(theme: AppTheme, onChange: (AppTheme) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        SettingsRow(
            title = stringResource(R.string.settings_theme),
            subtitle = theme.label(),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppTheme.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label()) },
                    onClick = {
                        onChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AppTheme.label(): String = stringResource(
    when (this) {
        AppTheme.System -> R.string.settings_theme_system
        AppTheme.Light -> R.string.settings_theme_light
        AppTheme.Dark -> R.string.settings_theme_dark
    },
)

/**
 * "Language", and the languages the app is translated into. Following the
 * device is the default, and where an untranslated language lands. The other
 * two name themselves: somebody who set the wrong one has to find the way back.
 */
@Composable
private fun LanguageRow(language: AppLanguage, onChange: (AppLanguage) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        SettingsRow(
            title = stringResource(R.string.settings_language),
            subtitle = language.label(),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppLanguage.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label()) },
                    onClick = {
                        onChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** An autonym for each language, and the device's own for [AppLanguage.System]. */
@Composable
private fun AppLanguage.label(): String = stringResource(
    when (this) {
        AppLanguage.System -> R.string.settings_language_system
        AppLanguage.English -> R.string.settings_language_english
        AppLanguage.French -> R.string.settings_language_french
    },
)

@Composable
private fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
