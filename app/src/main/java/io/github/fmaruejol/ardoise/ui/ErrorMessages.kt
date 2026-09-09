package io.github.fmaruejol.ardoise.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.core.instance.BaseUrl
import io.github.fmaruejol.ardoise.core.result.SpliitError

/**
 * Turns the sealed error types into something a person can act on, in one
 * place, so no raw exception message ever reaches a screen.
 */
@Composable
fun SpliitError.describe(): String = when (this) {
    is SpliitError.Network -> stringResource(R.string.error_network)

    is SpliitError.Http -> stringResource(R.string.error_http, status)

    // Most screens branch on this first: a missing group or expense is a state
    // they render themselves.
    SpliitError.NotFound -> stringResource(R.string.error_not_found)

    is SpliitError.Procedure -> stringResource(R.string.error_procedure, message)

    // Most often the address belongs to something that is not Spliit.
    is SpliitError.Malformed -> stringResource(R.string.error_malformed)
}

@Composable
fun BaseUrl.Reason.describe(): String = when (this) {
    BaseUrl.Reason.Empty -> stringResource(R.string.server_url_error_empty)
    BaseUrl.Reason.Malformed -> stringResource(R.string.server_url_error_malformed)
    BaseUrl.Reason.MissingHost -> stringResource(R.string.server_url_error_missing_host)
    BaseUrl.Reason.UnsupportedScheme -> stringResource(R.string.server_url_error_scheme)
}
