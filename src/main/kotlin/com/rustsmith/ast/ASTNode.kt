package com.rustsmith.ast

import com.rustsmith.CustomRandom
import com.rustsmith.generation.ASTGenerator
import com.rustsmith.generation.Context
import com.rustsmith.generation.IdentGenerator
import com.rustsmith.recondition.Macros

sealed interface ASTNode {
    fun toRust(): String
}

data class FunctionDefinition(
    val returnType: Type = VoidType,
    val functionName: String,
    val arguments: Map<String, Type>,
    val body: StatementBlock,
    val forceNoInline: Boolean,
    val addSelfVariable: Boolean
) : ASTNode {
    override fun toRust(): String {
        val inline = "" // if (forceNoInline) "#[inline(never)]" else ""
        val self = if (addSelfVariable) "&self," else ""
        if (addSelfVariable) {
            return "$inline\npub fn $functionName($self ${
            arguments.map { "${it.key}: ${it.value.toRust()}" }.joinToString(", ")
            }) -> ${returnType.toRust()} {\n${body.toRust()}\n}\n"
        } else {
            return "$inline\nfn $functionName($self ${
            arguments.map { "${it.key}: ${it.value.toRust()}" }.joinToString(", ")
            }) -> ${returnType.toRust()} {\n${body.toRust()}\n}\n"
        }
    }
}

fun collectStructTypes(type: Type): MutableSet<StructType> {
    var curStructList = mutableSetOf<StructType>()
    // println("          ${type::class}")
    when (type) {
        is StructType -> {
            curStructList.add(type)
            type.types.forEach { intype ->
                curStructList.addAll(collectStructTypes(intype.second))
            }
        }
        is TupleType -> {
            type.types.forEach { intype ->
                curStructList.addAll(collectStructTypes(intype))
            }
        }
        is StaticSizedArrayType -> curStructList.addAll(collectStructTypes(type.internalType))
        is VectorType -> curStructList.addAll(collectStructTypes(type.type))
        is BoxType -> curStructList.addAll(collectStructTypes(type.internalType))
        is OptionType -> curStructList.addAll(collectStructTypes(type.type))
        is TypeAliasType -> curStructList.addAll(collectStructTypes(type.internalType))
        is LifetimeParameterizedType<*> -> curStructList.addAll(collectStructTypes(type.type))
        else -> {
        }
    }
    return curStructList
}

data class StructDefinition(val structType: LifetimeParameterizedType<StructType>, val methods: MutableList<FunctionDefinition> = mutableListOf()) : ASTNode {
    override fun toRust(): String {
        var returnStructList = mutableSetOf<StructType>()
        // get the struct types of the return type of each method
        methods.forEach { method ->
            returnStructList.addAll(collectStructTypes(method.returnType))
        }

        // println("Begin --------${structType.type.toRust()}")
        // get the struct types defined inside the current struct
        structType.type.types.forEach { intype ->
            // println("${intype.first}${intype.second::class}")
            returnStructList.addAll(collectStructTypes(intype.second))
        }
        // println("End --------")
        val traits = "#[near_bindgen]\n#[derive(BorshDeserialize, BorshSerialize)]\n"
        val pubtraits = "#[derive(BorshDeserialize, BorshSerialize, Serialize)]\n"
        val macros = "#[near_bindgen]\n"
        val pubmacros = "#[serde(crate = \"near_sdk::serde\")]\n"
        val parameterizedSyntax = if (structType.lifetimeParameters().isNotEmpty()) "<${structType.lifetimeParameters().toSet().joinToString(",") { "'a$it" }}>" else ""

        /* if (structType.type.methodNum == 0) {
            val structDef = "${pubtraits}${pubmacros}pub struct ${structType.type.structName}$parameterizedSyntax {\n${structType.type.types.joinToString("\n") { "${it.first}: ${it.second.toRust()}," }}\n}\n"
            val defaultMethod = ""
            val implDef = ""
            return structDef + defaultMethod + implDef
        } */

        if (returnStructList.size > 0) {
            val structDef = "struct ${structType.type.structName}$parameterizedSyntax {\n${structType.type.types.joinToString("\n") { "${it.first}: ${it.second.toRust()}," }}\n}\n"

            val defaultMethod = "impl Default for ${structType.type.structName}{\nfn default() -> Self {\n${structType.type.defaultMethod}\n}\n}"

            val implDef = "\nimpl$parameterizedSyntax ${structType.type.structName}$parameterizedSyntax {\n ${methods.joinToString("\n") { it.toRust() }} \n}"
            return structDef + defaultMethod + implDef
        }

        val structDef = "${traits}struct ${structType.type.structName}$parameterizedSyntax {\n${structType.type.types.joinToString("\n") { "${it.first}: ${it.second.toRust()}," }}\n}\n"

        val defaultMethod = "impl Default for ${structType.type.structName}{\nfn default() -> Self {\n${structType.type.defaultMethod}\n}\n}"

        val implDef = "\n${macros}impl$parameterizedSyntax ${structType.type.structName}$parameterizedSyntax {\n ${methods.joinToString("\n") { it.toRust() }} \n}"
        return structDef + defaultMethod + implDef
    }
}

data class TypeAliasDefinition(val aliasType: LifetimeParameterizedType<TypeAliasType>) : ASTNode {
    override fun toRust(): String {
        val parameterizedSyntax = if (aliasType.lifetimeParameters().isNotEmpty()) "<${aliasType.lifetimeParameters().toSet().joinToString(",") { "'a$it" }}>" else ""
        return "type ${aliasType.type.typeAliasName}$parameterizedSyntax = ${aliasType.type.internalType.toRust()};"
    }
}

data class Program(
    val seed: Long,
    val macros: Set<Macros>,
    val constants: List<ConstDeclaration>,
    val aliases: List<TypeAliasDefinition>,
    val structs: List<StructDefinition> = emptyList(),
    val functions: List<FunctionDefinition>
) :
    ASTNode {
    override fun toRust(): String {
        return "use near_sdk::borsh::{self, BorshDeserialize, BorshSerialize};\nuse near_sdk::serde::Serialize;\nuse near_sdk::{env, AccountId, Balance, near_bindgen};\nuse near_sdk::collections::{Vector};\nuse near_sdk::json_types::{U128};\n${constants.joinToString("\n") { it.toRust() }}\n${macros.joinToString("\n") { it.toRust() }}\n${structs.joinToString("\n") { it.toRust() }}\n${aliases.joinToString("\n") { it.toRust() }}\n${
        functions.joinToString(
            "\n"
        ) { it.toRust() }
        }"
    }
}

fun generateProgram(programSeed: Long, identGenerator: IdentGenerator, failFast: Boolean): Pair<Program, List<String>> {
    val functionSymbolTable = FunctionSymbolTable()
    val globalSymbolTable = GlobalSymbolTable()
    val symbolTable = SymbolTable(SymbolTable(null, functionSymbolTable, globalSymbolTable), functionSymbolTable, globalSymbolTable)
    val astGenerator = ASTGenerator(symbolTable, failFast, identGenerator)
    val mainFunctionContext = Context(listOf(mapOf()), "main", listOf(), symbolTable)
    val numberOfConstants = CustomRandom.nextInt(10)
    val constantDeclarations = (0..numberOfConstants).map { astGenerator.generateConstantDeclaration(mainFunctionContext) }
    val body = astGenerator(mainFunctionContext)
    val bodyWithOutput =
        StatementBlock(listOf(FetchCLIArgs(symbolTable)) + body.statements + Output(symbolTable, programSeed), symbolTable)
    val mainFunction = FunctionDefinition(
        functionName = "main",
        arguments = emptyMap(),
        body = bodyWithOutput,
        forceNoInline = false,
        addSelfVariable = false
    )
    val cliArguments = symbolTable.globalSymbolTable.commandLineTypes.map { astGenerator.generateCLIArgumentsForLiteralType(it, mainFunctionContext) }
    return Program(programSeed, setOf(), constantDeclarations, globalSymbolTable.typeAliases.toList(), globalSymbolTable.structs.toList(), functionSymbolTable.functions) to cliArguments
}
