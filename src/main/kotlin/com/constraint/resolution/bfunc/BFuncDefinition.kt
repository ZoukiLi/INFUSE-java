package com.constraint.resolution.bfunc

import java.io.File

/**
 * 表示一个bfunc函数定义
 */
class BFuncDefinition(val bfunc: BFunc) {
    /**
     * 函数名称
     */
    val name: String get() = bfunc.name
    
    /**
     * 函数参数列表
     */
    val params: List<String> get() = bfunc.params
    
    /**
     * 解析表达式
     */
    fun parseExpression(): BFuncExpression {
        return AdapterBFuncExpression(bfunc.body)
    }
    
    companion object {
        /**
         * 从JSON字符串创建BFuncDefinition
         */
        fun fromJson(jsonString: String): BFuncDefinition {
            val parser = BFuncParser()
            val bfunc = parser.parse(jsonString)
            return BFuncDefinition(bfunc)
        }
        
        /**
         * 从JSON文件创建BFuncDefinition
         */
        fun fromJsonFile(filePath: String): BFuncDefinition {
            val jsonString = File(filePath).readText()
            return fromJson(jsonString)
        }
    }
}

/**
 * 管理bfunc定义的注册表
 */
class BFuncRegistry {
    private val registry = mutableMapOf<String, BFuncDefinition>()
    
    /**
     * 注册一个bfunc定义
     */
    fun registerBFunc(definition: BFuncDefinition) {
        registry[definition.name] = definition
    }
    
    /**
     * 根据名称获取bfunc定义
     */
    fun getBFuncDefinition(name: String): BFuncDefinition? {
        return registry[name]
    }
    
    /**
     * 从目录加载所有bfunc定义
     */
    fun loadFromDirectory(dirPath: String) {
        val dir = File(dirPath)
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles { file -> file.extension == "json" }?.forEach { file ->
                try {
                    val definition = BFuncDefinition.fromJsonFile(file.absolutePath)
                    registerBFunc(definition)
                } catch (e: Exception) {
                    println("Error loading bfunc from ${file.absolutePath}: ${e.message}")
                }
            }
        }
    }
}
