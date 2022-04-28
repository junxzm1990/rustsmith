package com.rustsmith.generation

import com.rustsmith.ast.*
import com.rustsmith.subclasses
import kotlin.reflect.KClass

data class Context(
    private val nodeDepthState: Map<KClass<out ASTNode>, Int>,
    val statementsPerScope: List<List<KClass<out Statement>>>,
    val symbolTable: SymbolTable,
    val requiredType: Type? = null,
    val returnExpressionType: Type? = null,
    val previousIncrement: KClass<out ASTNode>? = null
) {
    val numberOfDeclarationsLocal = lazy { symbolTable.getLocalVariables().size }
    val numberOfDeclarationsInScope = lazy { symbolTable.getCurrentVariables().size }
    val numberOfFunctionsDefined = lazy { symbolTable.functionSymbolTable.functions.size }
    val numberOfStructsDefined = lazy { symbolTable.globalSymbolTable.structs.size }
    val numberOfTuplesDefined = lazy { symbolTable.globalSymbolTable.tupleTypes.size }

    fun setRequiredType(type: Type): Context {
        return this.copy(requiredType = type)
    }

    fun setReturnExpressionType(type: Type): Context {
        return this.copy(returnExpressionType = type)
    }

    fun getDepth(kClass: KClass<out ASTNode>): Int {
        return kClass.subclasses().sumOf { nodeDepthState[it] ?: 0 }
    }

    fun withSymbolTable(symbolTable: SymbolTable): Context {
        val stateCopy = nodeDepthState.toMutableMap().withDefault { 0 }
        return this.copy(nodeDepthState = stateCopy, statementsPerScope = statementsPerScope.toMutableList(), symbolTable = symbolTable)
    }

    fun incrementCount(kClass: KClass<out ASTNode>): Context {
        val stateCopy = nodeDepthState.toMutableMap().withDefault { 0 }
        stateCopy[kClass] = stateCopy.getValue(kClass) + 1
        return this.copy(
            nodeDepthState = stateCopy,
            statementsPerScope = statementsPerScope.toMutableList(),
            previousIncrement = kClass
        )
    }

    fun incrementStatementCount(statement: KClass<out Statement>): Context {
        val stateCopy = nodeDepthState.toMutableMap().withDefault { 0 }
        val statementDepthCopy = statementsPerScope.toMutableList()
        statementDepthCopy[statementDepthCopy.lastIndex] = listOf(*statementDepthCopy[statementDepthCopy.lastIndex].toTypedArray(), statement)
        return this.copy(nodeDepthState = stateCopy, statementsPerScope = statementDepthCopy)
    }

    fun enterScope(): Context {
        val stateCopy = nodeDepthState.toMutableMap().withDefault { 0 }
        return this.copy(nodeDepthState = stateCopy, statementsPerScope = listOf(*statementsPerScope.toTypedArray(), listOf()))
    }

    fun resetContextForFunction(): Context {
        return this.copy(statementsPerScope = listOf(), previousIncrement = null)
    }
}
