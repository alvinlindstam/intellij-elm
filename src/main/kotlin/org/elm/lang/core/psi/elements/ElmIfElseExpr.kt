package org.elm.lang.core.psi.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import org.elm.lang.core.psi.ElmAtomTag
import org.elm.lang.core.psi.ElmExpressionTag
import org.elm.lang.core.psi.ElmPsiElementImpl

/**
 * An if-else expression, possible with one or more else-if branches.
 *
 * e.g.
 * - `if True then 1 else 2`
 * - `if False then 1 else if True then 2 else 3`
 */
class ElmIfElseExpr(node: ASTNode) : ElmPsiElementImpl(node), ElmAtomTag {

    /**
     * In a well-formed program, will contain an odd number of expressions, with at least three.
     */
    val expressionList: List<ElmExpressionTag>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(this, ElmExpressionTag::class.java)

}
