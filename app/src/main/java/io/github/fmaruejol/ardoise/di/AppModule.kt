package io.github.fmaruejol.ardoise.di

import android.util.Log
import io.github.fmaruejol.ardoise.BuildConfig
import io.github.fmaruejol.ardoise.api.DefaultSpliitApiFactory
import io.github.fmaruejol.ardoise.api.ExchangeRates
import io.github.fmaruejol.ardoise.api.FrankfurterExchangeRates
import io.github.fmaruejol.ardoise.api.HttpLogger
import io.github.fmaruejol.ardoise.api.SpliitApi
import io.github.fmaruejol.ardoise.api.SpliitApiClient
import io.github.fmaruejol.ardoise.api.SpliitApiFactory
import io.github.fmaruejol.ardoise.api.spliitHttpClient
import io.github.fmaruejol.ardoise.api.trpc.BaseUrlProvider
import io.github.fmaruejol.ardoise.api.trpc.TrpcClient
import io.github.fmaruejol.ardoise.core.prefs.GroupPreferences
import io.github.fmaruejol.ardoise.data.ActivityRepository
import io.github.fmaruejol.ardoise.data.AndroidConnectivity
import io.github.fmaruejol.ardoise.data.AndroidNetworkAccess
import io.github.fmaruejol.ardoise.data.BalanceRepository
import io.github.fmaruejol.ardoise.data.CategoryRepository
import io.github.fmaruejol.ardoise.data.Connectivity
import io.github.fmaruejol.ardoise.data.DataStoreGroupPreferences
import io.github.fmaruejol.ardoise.data.ExpenseOutbox
import io.github.fmaruejol.ardoise.data.ExpenseRepository
import io.github.fmaruejol.ardoise.data.GroupRepository
import io.github.fmaruejol.ardoise.data.LanguageRepository
import io.github.fmaruejol.ardoise.data.NetworkAccess
import io.github.fmaruejol.ardoise.data.ServerRepository
import io.github.fmaruejol.ardoise.data.ThemeRepository
import io.github.fmaruejol.ardoise.data.local.SpliitCache
import io.github.fmaruejol.ardoise.data.local.outbox.OutboxDatabase
import io.github.fmaruejol.ardoise.data.local.outbox.outboxDatabase
import io.github.fmaruejol.ardoise.data.local.spliitDatabase
import io.github.fmaruejol.ardoise.data.spliitPreferencesDataStore
import io.github.fmaruejol.ardoise.ui.AppViewModel
import io.github.fmaruejol.ardoise.ui.activity.ActivityViewModel
import io.github.fmaruejol.ardoise.ui.balances.BalancesViewModel
import io.github.fmaruejol.ardoise.ui.balances.SettleUpViewModel
import io.github.fmaruejol.ardoise.ui.creategroup.CreateGroupViewModel
import io.github.fmaruejol.ardoise.ui.expense.ExpenseDetailViewModel
import io.github.fmaruejol.ardoise.ui.expense.ExpenseFormViewModel
import io.github.fmaruejol.ardoise.ui.expense.ReimbursementPrefill
import io.github.fmaruejol.ardoise.ui.group.GroupViewModel
import io.github.fmaruejol.ardoise.ui.grouplist.GroupListViewModel
import io.github.fmaruejol.ardoise.ui.groupsettings.GroupSettingsViewModel
import io.github.fmaruejol.ardoise.ui.home.HomeViewModel
import io.github.fmaruejol.ardoise.ui.join.JoinGroupViewModel
import io.github.fmaruejol.ardoise.ui.server.ServerViewModel
import io.github.fmaruejol.ardoise.ui.settings.SettingsViewModel
import io.github.fmaruejol.ardoise.ui.totals.TotalsViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The whole dependency graph. **Constructor injection only**: nothing outside
 * this file calls `get()` or `inject()`, which keeps a later move to Hilt a
 * change to one file.
 */
val networkModule: Module = module {
    // Its own definition so a test can swap the engine.
    single<HttpClientEngine> { OkHttp.create() }
    single<HttpClient> {
        spliitHttpClient(
            engine = get(),
            // Debug only: request URLs carry group ids, the only credential Spliit has.
            logger = if (BuildConfig.DEBUG) HttpLogger { Log.d("ArdoiseHttp", it) } else null,
        )
    }

    // Read per request, so switching instance needs no restart.
    single<BaseUrlProvider> { BaseUrlProvider { get<GroupPreferences>().baseUrl.first() } }

    single { TrpcClient(httpClient = get(), baseUrl = get()) }
    single<SpliitApi> { SpliitApiClient(client = get()) }

    // A client for one address, so a server can be checked before it is saved.
    single<SpliitApiFactory> { DefaultSpliitApiFactory(httpClient = get()) }

    // Not Spliit's: the rate source the web client uses. See ExchangeRates.
    single<ExchangeRates> { FrankfurterExchangeRates(client = get()) }
}

val dataModule: Module = module {
    // Injected rather than reached for, so a test can drive what runs on it.
    single<CoroutineDispatcher> { Dispatchers.IO }

    single<GroupPreferences> { DataStoreGroupPreferences(spliitPreferencesDataStore(androidContext())) }

    // One database and one cache for the process: Room's DAO Flows only wake
    // each other when they are looking at the same tables.
    single { spliitDatabase(androidContext()) }
    single { SpliitCache(api = get(), database = get()) }

    // The queue of expenses typed offline, in its own database: the one thing
    // stored here that the server has never seen.
    single { outboxDatabase(androidContext()) }
    single { get<OutboxDatabase>().outbox() }
    single {
        ExpenseOutbox(api = get(), dao = get(), cache = get(), preferences = get())
    }

    single { GroupRepository(api = get(), preferences = get(), cache = get(), outbox = get()) }
    single {
        ExpenseRepository(api = get(), preferences = get(), cache = get(), outbox = get())
    }
    single { BalanceRepository(api = get(), preferences = get(), cache = get()) }
    single { ActivityRepository(cache = get()) }
    single { CategoryRepository(cache = get()) }
    single { ServerRepository(preferences = get(), apiFactory = get()) }
    single { LanguageRepository(preferences = get()) }
    single { ThemeRepository(preferences = get()) }
    single<NetworkAccess> { AndroidNetworkAccess(androidContext(), io = get()) }
    single<Connectivity> { AndroidConnectivity(androidContext()) }
}

val viewModelModule: Module = module {
    viewModel { ServerViewModel(repository = get(), groups = get(), networkAccess = get()) }
    viewModel {
        GroupListViewModel(repository = get(), balances = get(), connectivity = get())
    }
    // The group id comes from the navigation argument, not from the graph.
    viewModel { (groupId: String) ->
        GroupSettingsViewModel(groupId = groupId, repository = get(), serverRepository = get())
    }
    viewModel { (groupId: String) ->
        GroupViewModel(
            groupId = groupId,
            groups = get(),
            expenses = get(),
            categories = get(),
            connectivity = get(),
        )
    }
    viewModel { (groupId: String, expenseId: String) ->
        ExpenseDetailViewModel(
            groupId = groupId,
            expenseId = expenseId,
            groups = get(),
            expenses = get(),
            connectivity = get(),
        )
    }
    // A null expense id means a new expense; a pending id means one still
    // waiting to be sent; the prefill is set only by "Mark as paid".
    viewModel { params ->
        val (
            groupId: String,
            expenseId: String?,
            pendingId: String?,
            prefill: ReimbursementPrefill?,
        ) = params
        ExpenseFormViewModel(
            groupId = groupId,
            expenseId = expenseId,
            pendingId = pendingId,
            prefill = prefill,
            groups = get(),
            expenses = get(),
            categories = get(),
            rates = get(),
            savedState = params.get(),
        )
    }
    viewModel { (groupId: String) ->
        BalancesViewModel(
            groupId = groupId,
            groups = get(),
            balances = get(),
            connectivity = get(),
        )
    }
    viewModel { (groupId: String) ->
        SettleUpViewModel(
            groupId = groupId,
            groups = get(),
            balances = get(),
            connectivity = get(),
        )
    }
    viewModel { (groupId: String) ->
        ActivityViewModel(
            groupId = groupId,
            activities = get(),
            groups = get(),
            connectivity = get(),
        )
    }
    viewModel { (groupId: String) ->
        TotalsViewModel(
            groupId = groupId,
            groups = get(),
            expenses = get(),
            connectivity = get(),
        )
    }
    viewModel { JoinGroupViewModel(repository = get()) }
    viewModel { params -> CreateGroupViewModel(repository = get(), savedState = params.get()) }
    viewModel {
        SettingsViewModel(
            serverRepository = get(),
            groups = get(),
            languages = get(),
            themes = get(),
        )
    }
    viewModel { HomeViewModel(serverRepository = get()) }
    viewModel {
        AppViewModel(
            serverRepository = get(),
            groupRepository = get(),
            languageRepository = get(),
            themeRepository = get(),
            expenses = get(),
            connectivity = get(),
        )
    }
}

val appModules: List<Module> = listOf(networkModule, dataModule, viewModelModule)
