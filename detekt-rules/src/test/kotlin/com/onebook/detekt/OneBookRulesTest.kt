package com.onebook.detekt

import io.github.detekt.test.utils.compileContentForTest
import io.gitlab.arturbosch.detekt.test.lint
import org.jetbrains.kotlin.resolve.BindingContext
import kotlin.test.Test
import kotlin.test.assertEquals

class OneBookRulesTest {
    @Test fun `single parameter declarations must wrap`() {
        assertEquals(1, ComposableMultilineArguments().lint("@Composable fun Card(title: String) {}").size)
        assertEquals(0, ComposableMultilineArguments().lint("""
            @Composable fun Card(
                title: String,
                onClick: () -> Unit,
            ) {}
        """.trimIndent()).size)
    }

    @Test fun `single argument and multiple arguments must wrap`() {
        assertEquals(1, ComposableMultilineArguments().lint("fun ui() { Text(\"title\") }").size)
        assertEquals(1, ComposableMultilineArguments().lint("fun ui() { Text(\"title\", color = red) }").size)
        assertEquals(0, ComposableMultilineArguments().lint("""
            fun ui() {
                Button(
                    onClick = {},
                ) { Text(
                    "title",
                ) }
            }
        """.trimIndent()).size)
    }

    @Test fun `regular calls and empty lists are allowed`() {
        assertEquals(0, ComposableMultilineArguments().lint("fun ui() { load(1, 2); VerticalDivider() }").size)
    }

    @Test fun `import aliases are checked`() {
        assertEquals(1, ComposableMultilineArguments().lint("""
            import androidx.compose.material3.Text as Label
            fun ui() { Label("title") }
        """.trimIndent()).size)
    }

    @Test fun `project composables are indexed across files`() {
        val declaration = compileContentForTest("@Composable fun CustomWidget() {}")
        val use = compileContentForTest("fun ui() { CustomWidget(1) }")
        ProjectIndex().onStart(listOf(declaration, use), BindingContext.EMPTY)
        assertEquals(1, ComposableMultilineArguments().lint(use).size)
    }

    @Test fun `preview must call its component and does not require its own preview`() {
        assertEquals(1, ComposablePreview().lint("@Composable fun Card() {}").size)
        assertEquals(1, ComposablePreview().lint("""
            @Composable fun Card() {}
            @Preview @Composable fun CardPreview() {}
        """.trimIndent()).size)
        assertEquals(0, ComposablePreview().lint("""
            @Composable fun Card() {}
            @Preview @Composable fun CardPreview() { Card() }
        """.trimIndent()).size)
    }

    @Test fun `viewmodel requires both interface and internal visibility`() {
        assertEquals(1, ViewModelInterface().lint("class DetailViewModel : ViewModel()").size)
        assertEquals(1, ViewModelInterface().lint("internal class DetailViewModel : ViewModel()").size)
        assertEquals(1, ViewModelInterface().lint("interface Contract; class DetailViewModel : ViewModel(), Contract").size)
        assertEquals(0, ViewModelInterface().lint("interface Contract; internal class DetailViewModel : ViewModel(), Contract").size)
    }

    @Test fun `viewmodel contract in another file is recognized`() {
        val contract = compileContentForTest("interface DetailViewModel")
        val implementation = compileContentForTest("internal class DetailViewModelImpl : ViewModel(), DetailViewModel")
        ProjectIndex().onStart(listOf(contract, implementation), BindingContext.EMPTY)
        assertEquals(0, ViewModelInterface().lint(implementation).size)
    }

    @Test fun `interface and implementation cannot share a file`() {
        assertEquals(1, InterfaceImplementationSeparation().lint(
            "interface DetailViewModel; internal class DetailViewModelImpl : DetailViewModel"
        ).size)
        assertEquals(1, InterfaceImplementationSeparation().lint(
            "interface Repository; class RepositoryImpl : Repository"
        ).size)
        val contract = compileContentForTest("interface DetailViewModel", "DetailViewModel.kt")
        val implementation = compileContentForTest(
            "internal class DetailViewModelImpl : ViewModel(), DetailViewModel", "DetailViewModelImpl.kt"
        )
        ProjectIndex().onStart(listOf(contract, implementation), BindingContext.EMPTY)
        assertEquals(0, InterfaceImplementationSeparation().lint(implementation).size)
    }

    @Test fun `primary viewmodel implementation uses Impl suffix and matching file name`() {
        val wrongName = compileContentForTest(
            "internal class DefaultDetailViewModel : ViewModel(), DetailViewModel", "DefaultDetailViewModel.kt"
        )
        assertEquals(2, ViewModelImplementationNaming().lint(wrongName).size)
        val wrongFile = compileContentForTest(
            "internal class DetailViewModelImpl : ViewModel(), DetailViewModel", "DetailViewModel.kt"
        )
        assertEquals(1, ViewModelImplementationNaming().lint(wrongFile).size)
        val implementation = compileContentForTest(
            "internal class DetailViewModelImpl : ViewModel(), DetailViewModel", "DetailViewModelImpl.kt"
        )
        assertEquals(0, ViewModelImplementationNaming().lint(implementation).size)
    }

    @Test fun `flow override does not wrap a backing property`() {
        assertEquals(1, DirectMutableStateFlow().lint("""
            class Example {
                private val mutableState = MutableStateFlow(BooksState())
                override val state = mutableState.asStateFlow()
            }
        """.trimIndent()).size)
        assertEquals(1, DirectMutableStateFlow().lint("""
            class Example {
                private val backing = MutableStateFlow(BooksState())
                override val state = backing
            }
        """.trimIndent()).size)
        assertEquals(1, DirectMutableStateFlow().lint("""
            class Example {
                private val backing = MutableStateFlow(BooksState())
                override val state get() = backing.asStateFlow()
            }
        """.trimIndent()).size)
    }

    @Test fun `viewmodel filename check accepts absolute parser paths`() {
        for (path in listOf("C:\\project\\DetailViewModelImpl.kt", "/project/DetailViewModelImpl.kt")) {
            val implementation = compileContentForTest(
                "internal class DetailViewModelImpl : ViewModel(), DetailViewModel", path
            )
            assertEquals(0, ViewModelImplementationNaming().lint(implementation).size)
        }
    }

    @Test fun `direct mutable flows and forwarded state are allowed`() {
        assertEquals(0, DirectMutableStateFlow().lint("""
            class Example {
                override val state = MutableStateFlow(BooksState())
                override val query = MutableStateFlow("")
                override val message = MutableStateFlow<String?>(null)
            }
        """.trimIndent()).size)
        assertEquals(0, DirectMutableStateFlow().lint("""
            class Example {
                override val state = player.state
            }
        """.trimIndent()).size)
    }
}
