package com.onebook.detekt

import org.jetbrains.kotlin.com.intellij.openapi.util.Key
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import io.gitlab.arturbosch.detekt.api.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.resolve.BindingContext

private fun KtAnnotated.hasAnnotation(name: String): Boolean =
    annotationEntries.any { it.shortName?.asString() == name }

private fun KtClass.parents(): List<String> = superTypeListEntries.map {
    it.typeReference?.text.orEmpty().substringBefore("<").substringBefore("(").substringAfterLast(".")
}

internal data class SourceIndex(val composables: Set<String>, val classes: Map<String, KtClass>)
internal val sourceIndexKey = Key<SourceIndex>("onebook.sourceIndex")

class ProjectIndex : FileProcessListener {
    override fun onStart(files: List<KtFile>, bindingContext: BindingContext) {
        val index = SourceIndex(
            files.flatMap { it.collectDescendantsOfType<KtNamedFunction>() }
                .filter { it.hasAnnotation("Composable") }.mapNotNull { it.name }.toSet(),
            files.flatMap { it.collectDescendantsOfType<KtClass>() }
                .mapNotNull { klass -> klass.name?.let { it to klass } }.toMap(),
        )
        files.forEach { it.putUserData(sourceIndexKey, index) }
    }
}

class OneBookRuleSetProvider : RuleSetProvider {
    override val ruleSetId = "onebook"
    override fun instance(config: Config) = RuleSet(
        ruleSetId,
        listOf(
            ComposableMultilineArguments(config), ComposablePreview(config), ViewModelInterface(config),
            InterfaceImplementationSeparation(config), ViewModelImplementationNaming(config),
            DirectMutableStateFlow(config),
        ),
    )
}

class InterfaceImplementationSeparation(config: Config = Config.empty) : OneBookRule(
    config, "InterfaceImplementationSeparation", "Declare an interface and its implementation in different files.",
) {
    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)
        if (klass.isInterface()) return
        val interfaces = klass.containingKtFile.collectDescendantsOfType<KtClass>()
            .filter { it.isInterface() }.mapNotNull { it.name }.toSet()
        if (klass.parents().any { it in interfaces }) {
            finding(klass, "Move the implementation to a separate file from its interface.")
        }
    }
}

class ViewModelImplementationNaming(config: Config = Config.empty) : OneBookRule(
    config, "ViewModelImplementationNaming", "Name the primary implementation <Interface>Impl.",
) {
    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)
        if (klass.isInterface()) return
        val contract = klass.parents().firstOrNull { it != "ViewModel" && it.endsWith("ViewModel") }
            ?: return
        val expected = contract + "Impl"
        if (klass.name != expected) finding(klass, "Name this implementation $expected.")
        val fileName = klass.containingKtFile.name.replace('\\', '/').substringAfterLast('/')
        if (fileName != "$expected.kt") {
            finding(klass, "Declare this implementation in $expected.kt (found $fileName).")
        }
    }
}

class DirectMutableStateFlow(config: Config = Config.empty) : OneBookRule(
    config, "DirectMutableStateFlow", "Override flow properties directly without a private mutable backing property.",
) {
    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)
        if (!property.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return
        val body = property.initializer ?: property.getter?.bodyExpression ?: return
        val backingProperties = (property.parent as? KtClassBody)?.properties.orEmpty().filter {
            it.hasModifier(KtTokens.PRIVATE_KEYWORD) &&
                (it.initializer as? KtCallExpression)?.calleeExpression?.text == "MutableStateFlow"
        }.mapNotNull { it.name }.toSet()
        val wrapsFlow = body.collectDescendantsOfType<KtCallExpression>().any {
            it.calleeExpression?.text == "asStateFlow"
        }
        val usesBackingProperty = (body.collectDescendantsOfType<KtNameReferenceExpression>() +
            listOfNotNull(body as? KtNameReferenceExpression)).any { it.getReferencedName() in backingProperties }
        if (wrapsFlow || usesBackingProperty) {
            finding(property, "Use override val ${property.name} = MutableStateFlow(...) directly; keep StateFlow in the interface.")
        }
    }
}

abstract class OneBookRule(config: Config, id: String, description: String) : Rule(config) {
    override val issue = Issue(id, Severity.Style, description, Debt.FIVE_MINS)
    protected fun finding(element: PsiElement, message: String) =
        report(CodeSmell(issue, Entity.from(element), message))
}

class ComposableMultilineArguments(config: Config = Config.empty) : OneBookRule(
    config, "ComposableMultilineArguments", "Put each Compose parameter and argument on its own line.",
) {
    // External Compose APIs used by this project. Project composables are indexed automatically.
    private val externalFunctions = valueOrDefault("externalFunctions", listOf(
        "Column", "Row", "Text", "TextButton", "Button", "OutlinedCard",
        "LazyColumn", "LinearProgressIndicator", "OutlinedTextField", "Slider", "MaterialTheme",
        "Scaffold", "SnackbarHost", "Surface", "Children", "Window", "VerticalDivider",
        "CompositionLocalProvider", "LaunchedEffect", "remember", "rememberWindowState",
        "collectAsState", "viewModel", "rememberCoroutineScope",
    )).toSet()

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (function.hasAnnotation("Composable")) {
            function.valueParameterList?.let { checkLines(it, function.valueParameters) }
        }
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val name = expression.calleeExpression?.text?.substringAfterLast(".") ?: return
        val file = expression.containingKtFile
        val localNames = file.collectDescendantsOfType<KtNamedFunction>()
            .filter { it.hasAnnotation("Composable") }.mapNotNull { it.name }
        val originalName = file.importDirectives.firstOrNull {
            it.aliasName == name
        }?.importedFqName?.shortName()?.asString() ?: name
        if (originalName in externalFunctions || originalName in localNames ||
            originalName in file.getUserData(sourceIndexKey)?.composables.orEmpty()) {
            expression.valueArgumentList?.let { checkLines(it, it.arguments) }
        }
    }

    private fun checkLines(list: PsiElement, arguments: List<PsiElement>) {
        if (arguments.isEmpty()) return
        val source = list.containingFile.text
        fun line(offset: Int) = source.take(offset).count { it == '\n' }
        val starts = listOf(list.textRange.startOffset) + arguments.map { it.textRange.startOffset }
        val sameLine = starts.zipWithNext().any { (a, b) -> line(a) == line(b) }
        val closingOnArgumentLine = line(arguments.last().textRange.endOffset - 1) ==
            line(list.textRange.endOffset - 1)
        if (sameLine || closingOnArgumentLine) finding(list,
            "Each Compose argument/parameter and the closing parenthesis must start on a separate line.")
    }
}

class ComposablePreview(config: Config = Config.empty) : OneBookRule(
    config, "ComposablePreview", "Every UI composable needs an offline preview in the same file.",
) {
    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (!function.hasAnnotation("Composable") || function.hasAnnotation("Preview")) return
        val name = function.name ?: return
        val hasPreview = function.containingKtFile.collectDescendantsOfType<KtNamedFunction>().any { preview ->
            preview.hasAnnotation("Preview") && preview.hasAnnotation("Composable") &&
                preview.collectDescendantsOfType<KtCallExpression>().any { it.calleeExpression?.text == name }
        }
        if (!hasPreview) finding(function, "Add an @Preview @Composable function calling $name in the same file.")
    }
}

class ViewModelInterface(config: Config = Config.empty) : OneBookRule(
    config, "ViewModelInterface", "Keep ViewModel implementations internal and expose an interface.",
) {
    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)
        if (klass.isInterface()) return
        val classes = klass.containingKtFile.getUserData(sourceIndexKey)?.classes
            ?: klass.containingKtFile.collectDescendantsOfType<KtClass>().mapNotNull {
                it.name?.let { name -> name to it }
            }.toMap()
        fun isViewModel(current: KtClass, seen: Set<String> = emptySet()): Boolean =
            current.parents().any { parent ->
                parent == "ViewModel" || (parent !in seen && classes[parent]?.let {
                    !it.isInterface() && isViewModel(it, seen + parent)
                } == true)
            }
        if (!isViewModel(klass) && klass.name?.endsWith("ViewModel") != true) return
        fun hasInterface(current: KtClass, seen: Set<String> = emptySet()): Boolean =
            current.parents().any { parent ->
                parent !in seen && classes[parent]?.let {
                    it.isInterface() || hasInterface(it, seen + parent)
                } == true
            }
        if (!klass.hasModifier(KtTokens.INTERNAL_KEYWORD) || !hasInterface(klass)) {
            finding(klass, "Make the ViewModel implementation internal and implement a presentation interface.")
        }
    }
}
