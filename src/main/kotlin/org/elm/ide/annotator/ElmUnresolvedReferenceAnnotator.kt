package org.elm.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.elm.ide.intentions.AddImportIntention
import org.elm.ide.intentions.ElmMakeDeclarationIntentionAction
import org.elm.lang.core.psi.ElmFile
import org.elm.lang.core.psi.ElmQID
import org.elm.lang.core.psi.ancestors
import org.elm.lang.core.psi.elements.*
import org.elm.lang.core.resolve.reference.*
import org.elm.lang.core.resolve.scope.GlobalScope
import org.elm.lang.core.resolve.scope.ImportScope
import org.elm.lang.core.resolve.scope.ModuleScope


/**
 * A handler should return `true` if it handled the error and no further processing needs to be done.
 */
typealias BadRefHandler =
        (ref: PsiReference, element: PsiElement, holder: AnnotationHolder) -> Boolean


class ElmUnresolvedReferenceAnnotator : Annotator {

    // A chain of handlers to be executed sequentially when a reference cannot be resolved.
    // Order matters! Handlers earlier in the chain can short-circuit further evaluation.
    private val handlers = listOf<BadRefHandler>(
            ::handleTypeAnnotation,
            ::handleSafeToIgnore,
            ::handleModuleHiddenByAlias
    )

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        var refs = element.references.asSequence()

        // Pre-processing: ignore any qualified value/type refs where the module qualifier could not be resolved.
        // This is necessary because a single Psi element like ElmValueExpr can return multiple references:
        // one for the module name and the other for the value/type name. If the former reference cannot be resolved,
        // then the latter is guaranteed not to resolve. And we don't want to double-report the error, so we will
        // instead filter them out.
        if (refs.any { it is QualifiedModuleNameReference<*> && it.resolve() == null }) {
            refs = refs.filterNot { it is QualifiedTypeReference || it is QualifiedValueReference || it is QualifiedConstructorReference }
        }

        // Give each handler a chance to deal with the unresolved ref before falling back on an error
        outerLoop@
        for (ref in refs.filter { it.resolve() == null }) {
            for (handler in handlers) {
                if (handler(ref, element, holder)) {
                    continue@outerLoop
                }
            }

            // Generic unresolved ref error
            //
            // Most of the time an ElmReferenceElement is not the ancestor of any other ElmReferenceElement.
            // And in these cases, it's ok to treat the error as spanning the entire reference element.
            // However, in cases like ElmParametricTypeRef, its children can also be reference elements,
            // and so it is vital that we correctly mark the error only on the text range that
            // contributed the reference.
            val errorRange = when (element) {
                is ElmTypeRef -> element.upperCaseQID.textRange
                else -> element.textRange
            }
            holder.createErrorAnnotation(errorRange, "Unresolved reference '${ref.canonicalText}'")
                    .also { it.registerFix(AddImportIntention()) }
        }
    }

    private fun handleTypeAnnotation(ref: PsiReference, element: PsiElement, holder: AnnotationHolder): Boolean {
        if (element !is ElmTypeAnnotation) return false

        holder.createWeakWarningAnnotation(element, "'${ref.canonicalText}' does not exist")
                .also { it.registerFix(ElmMakeDeclarationIntentionAction()) }

        return true
    }

    private fun handleSafeToIgnore(ref: PsiReference, element: PsiElement, @Suppress("UNUSED_PARAMETER") holder: AnnotationHolder): Boolean {
        // Ignore refs to built-in types and values
        if (GlobalScope.allBuiltInSymbols.contains(ref.canonicalText))
            return true

        // Ignore refs to Kernel (JavaScript) modules
        if (element is ElmValueExpr && element.upperCaseQID?.isQualifiedNativeRef()
                ?: element.valueQID?.isQualifiedNativeRef() ?: false) {
            return true
        } else if (element is ElmImportClause && element.moduleQID.isQualifiedNativeRef()) {
            return true
        }

        // Ignore refs to type variables in a type annotation
        if (ref is TypeVariableReference && element.ancestors.any { it is ElmTypeAnnotation }) {
            return true
        }

        return false
    }

    // When a module is imported using an alias (e.g. `import Json.Decode as D`),
    // Elm prohibits the use of the original module name in qualified references.
    // So we will try to detect this condition and present a helpful error.
    private fun handleModuleHiddenByAlias(ref: PsiReference, element: PsiElement, holder: AnnotationHolder): Boolean {
        if (element !is ElmValueExpr && element !is ElmTypeRef) return false
        val elmFile = element.containingFile as? ElmFile ?: return false

        val qid: ElmQID = when (ref) {
            is QualifiedValueReference -> ref.valueQID
            is QualifiedConstructorReference -> ref.upperCaseQID
            is QualifiedTypeReference -> ref.upperCaseQID
            else -> return false
        }

        if (qid.qualifierPrefix.isEmpty()) return false

        val moduleName = qid.qualifierPrefix
        val importDecl = ModuleScope(elmFile).getImportDecls().find { it.moduleQID.text == moduleName } ?: return false
        val aliasName = importDecl.asClause?.upperCaseIdentifier?.text ?: return false

        val importScope = ImportScope.fromImportDecl(importDecl) ?: return false

        val exposedNames = when (ref) {
            is QualifiedValueReference -> importScope.getExposedValues()
            is QualifiedConstructorReference -> importScope.getExposedConstructors()
            is QualifiedTypeReference -> importScope.getExposedTypes()
            else -> return false
        }

        if (exposedNames.none { it.name == ref.canonicalText })
            return false

        // Success! The reference would have succeeded were it not for the alias.
        holder.createErrorAnnotation(element, "Unresolved reference '${ref.canonicalText}'. " +
                "Module '$moduleName' is imported as '$aliasName' and so you must use the alias here.")
        return true
    }
}

private fun ElmUpperCaseQID.isQualifiedNativeRef() =
        isQualified && isKernelModule(upperCaseIdentifierList)

private fun ElmValueQID.isQualifiedNativeRef() =
        isQualified && isKernelModule(upperCaseIdentifierList)

private fun isKernelModule(identifiers: List<PsiElement>): Boolean {
    val moduleName = identifiers.joinToString(".") { it.text }
    return moduleName.startsWith("Elm.Kernel.")
            || moduleName.startsWith("Native.") // TODO [drop 0.18] remove the "Native" clause
}
