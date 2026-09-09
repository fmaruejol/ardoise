import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.spotless)
}

val ktlintRules = mapOf(
    "ktlint_code_style" to "ktlint_official",
    "ij_kotlin_allow_trailing_comma" to true,
    "ij_kotlin_allow_trailing_comma_on_call_site" to true,
    "ktlint_standard_function-naming" to "disabled",
    "ktlint_standard_property-naming" to "disabled",
    "ktlint_standard_function-signature" to "disabled",
    "ktlint_standard_class-signature" to "disabled",
    "ktlint_standard_multiline-expression-wrapping" to "disabled",
    "ktlint_standard_chain-method-continuation" to "disabled",
    "ktlint_standard_argument-list-wrapping" to "disabled",
    "compose_allowed_composition_locals" to "LocalIsDarkTheme",
    "ktlint_compose_parameter-naming" to "disabled",
    "ktlint_compose_multiple-emitters-check" to "disabled",
)

allprojects {
    apply(plugin = "com.diffplug.spotless")

    configure<SpotlessExtension> {
        lineEndings = com.diffplug.spotless.LineEnding.UNIX

        kotlin {
            target("src/**/*.kt")
            ktlint(libs.versions.ktlint.get())
                .setEditorConfigPath(rootProject.file(".editorconfig"))
                .editorConfigOverride(ktlintRules)
                .customRuleSets(
                    listOf("io.nlopez.compose.rules:ktlint:${libs.versions.composeRules.get()}"),
                )
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(libs.versions.ktlint.get())
                .setEditorConfigPath(rootProject.file(".editorconfig"))
                .editorConfigOverride(ktlintRules)
        }
    }
}
