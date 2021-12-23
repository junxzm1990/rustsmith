package com.rustsmith.ast

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.rustsmith.Random
import com.rustsmith.SymbolTable
import java.math.BigInteger
import kotlin.random.asJavaRandom
import kotlin.reflect.full.isSubclassOf

@JsonIgnoreProperties(value = ["symbolTable"])
sealed interface Expression : ASTNode {
    val symbolTable: SymbolTable
}

@GenNode(1)
data class Int8Literal(val value: Int, override val symbolTable: SymbolTable) :
    Expression {

    companion object : Randomizeable<Int8Literal> {
        override fun createRandom(symbolTable: SymbolTable): Int8Literal {
            return Int8Literal(
                value = Random.nextBits(7),
                symbolTable
            )
        }
    }

    override fun toRust(): String {
        return "${value}i8"
    }
}

@GenNode
data class Int16Literal(val value: Int, override val symbolTable: SymbolTable) :
    Expression {

    companion object : Randomizeable<Int16Literal> {
        private val radixOptions = listOf(2, 8, 10, 16)
        override fun createRandom(symbolTable: SymbolTable): Int16Literal {
            return Int16Literal(
                value = Random.nextBits(15),
                symbolTable
            )
        }
    }

    override fun toRust(): String {
        return "${value}i16"
    }
}

@GenNode
data class Int32Literal(val value: Int, override val symbolTable: SymbolTable) :
    Expression {

    companion object : Randomizeable<Int32Literal> {
        override fun createRandom(symbolTable: SymbolTable): Int32Literal {
            return Int32Literal(
                value = Random.nextInt(),
                symbolTable
            )
        }
    }

    override fun toRust(): String {
        return "${value}i32"
    }
}

@GenNode
data class Int64Literal(val value: Long, override val symbolTable: SymbolTable) :
    Expression {

    companion object : Randomizeable<Int64Literal> {
        override fun createRandom(symbolTable: SymbolTable): Int64Literal {
            return Int64Literal(
                value = Random.nextLong(),
                symbolTable
            )
        }
    }

    override fun toRust(): String {
        return "${value}i64"
    }
}

@GenNode
data class Int128Literal(val value: BigInteger, override val symbolTable: SymbolTable) :
    Expression {

    companion object : Randomizeable<Int128Literal> {
        override fun createRandom(symbolTable: SymbolTable): Int128Literal {
            return Int128Literal(
                value = BigInteger(127, Random.asJavaRandom()),
                symbolTable
            )
        }
    }

    override fun toRust(): String {
        return "${value}i128"
    }
}

@GenNode
data class Float32Literal(val value: Float, override val symbolTable: SymbolTable) : Expression {
    companion object : Randomizeable<Float32Literal> {
        override fun createRandom(symbolTable: SymbolTable): Float32Literal {
            return Float32Literal(value = Random.nextFloat(), symbolTable)
        }
    }

    override fun toRust(): String {
        return "${value}f32"
    }
}

@GenNode
data class Float64Literal(val value: Double, override val symbolTable: SymbolTable) : Expression {
    companion object : Randomizeable<Float64Literal> {
        override fun createRandom(symbolTable: SymbolTable): Float64Literal {
            return Float64Literal(value = Random.nextDouble(), symbolTable)
        }
    }

    override fun toRust(): String {
        return "${value}f64"
    }
}

@GenNode
data class StringLiteral(val value: String, override val symbolTable: SymbolTable) : Expression {

    companion object : Randomizeable<StringLiteral> {
        private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

        override fun createRandom(symbolTable: SymbolTable): StringLiteral {
            return StringLiteral(
                (1..Random.nextInt(100)).map { charPool[Random.nextInt(0, charPool.size)] }
                    .joinToString(""),
                symbolTable
            )
        }
    }

    override fun toRust(): String {
        return "\"$value\""
    }
}

@GenNode
data class Variable(val value: String, override val symbolTable: SymbolTable) : Expression {

    companion object : Randomizeable<Variable> {
        override fun createRandom(symbolTable: SymbolTable): Variable? {
            val value = symbolTable.getCurrentVariables().randomOrNull(Random) ?: return null
            return Variable(value, symbolTable)
        }
    }

    override fun toRust(): String {
        return value
    }
}

interface RecursiveExpression

@GenNode
data class AddExpression(
    val expr1: Expression,
    val expr2: Expression,
    override val symbolTable: SymbolTable
) : Expression, RecursiveExpression {

    companion object : Randomizeable<AddExpression> {
        override fun createRandom(symbolTable: SymbolTable): AddExpression {
            val type = generateSubClassList(Number::class).random(Random)
            val depth = Thread.currentThread().stackTrace.size
            val exp1: Expression = generateASTNode(symbolTable, { it.toType()::class == type }) {
                if (depth > 20) !it.isSubclassOf(RecursiveExpression::class) else true
            }
            val exp2: Expression = generateASTNode(symbolTable, { it.toType()::class == type }) {
                if (depth > 20) !it.isSubclassOf(RecursiveExpression::class) else true
            }
            return AddExpression(exp1, exp2, symbolTable)
        }
    }

    override fun toRust(): String {
        return "${expr1.toRust()} + ${expr2.toRust()}"
    }
}

@GenNode
data class DivideExpression(
    val expr1: Expression,
    val expr2: Expression,
    override val symbolTable: SymbolTable
) : Expression, RecursiveExpression {

    companion object : Randomizeable<AddExpression> {
        override fun createRandom(symbolTable: SymbolTable): DivideExpression {
            val type = generateSubClassList(Number::class).random(Random)
            val depth = Thread.currentThread().stackTrace.size
            val exp1: Expression = generateASTNode(symbolTable, { it.toType()::class == type }) {
                if (depth > 20) !it.isSubclassOf(RecursiveExpression::class) else true
            }
            val exp2: Expression = generateASTNode(symbolTable, { it.toType()::class == type }) {
                if (depth > 20) !it.isSubclassOf(RecursiveExpression::class) else true
            }
            return DivideExpression(exp1, exp2, symbolTable)
        }
    }

    override fun toRust(): String {
        return "${expr1.toRust()} / ${expr2.toRust()}"
    }
}

data class WrappingAdd(val addExpression: AddExpression, override val symbolTable: SymbolTable) : Expression {
    override fun toRust(): String {
        return "${addExpression.expr1.toRust()}.wrapping_add(${addExpression.expr2.toRust()})"
    }
}

data class ReconditionedDivision(val divideExpression: DivideExpression, override val symbolTable: SymbolTable) :
    Expression {
    override fun toRust(): String {
        val zeroExpression = (divideExpression.expr1.toType() as Number).zero(symbolTable)
        return "(if (${divideExpression.expr2.toRust()} != ${zeroExpression.toRust()}) {${divideExpression.expr1.toRust()} / ${divideExpression.expr2.toRust()}} else {${zeroExpression.toRust()}})"
    }
}

fun Expression.toType(): Type {
    return when (this) {
        is Int8Literal -> I8Type
        is Int16Literal -> I16Type
        is Int32Literal -> I32Type
        is Int64Literal -> I64Type
        is Int128Literal -> I128Type
        is Float32Literal -> F32Type
        is Float64Literal -> F64Type
        is StringLiteral -> StringType
        is Variable -> symbolTable[this.value]!!.type
        is AddExpression -> this.expr1.toType()
        is DivideExpression -> this.expr1.toType()
        is WrappingAdd -> this.addExpression.toType()
        is ReconditionedDivision -> this.divideExpression.toType()
    }
}
