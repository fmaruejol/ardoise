package io.github.fmaruejol.ardoise.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import io.github.fmaruejol.ardoise.api.trpc.BaseUrlProvider
import io.github.fmaruejol.ardoise.ui.expense.ReimbursementPrefill
import io.ktor.client.engine.HttpClientEngine
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

/**
 * Koin resolves at runtime, so a missing binding would otherwise crash on the
 * screen that needed it.
 */
@OptIn(KoinExperimentalAPI::class)
class AppModuleTest {
    @Test
    fun `dependency graph resolves`() {
        val wholeGraph = module { includes(appModules) }

        // These are supplied by the platform, by a lambda at startup or by a
        // navigation argument, not built from a constructor, so verify()
        // cannot introspect them.
        wholeGraph.verify(
            extraTypes = listOf(
                Context::class,
                HttpClientEngine::class,
                BaseUrlProvider::class,
                ReimbursementPrefill::class,
                SavedStateHandle::class,
            ),
        )
    }
}
