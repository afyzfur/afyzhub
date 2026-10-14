package com.afyzfur.afyzhub.ui.settings

import com.afyzfur.afyzhub.domain.model.PromptGroup
import kotlinx.serialization.json.*
import java.io.InputStream
import java.util.Base64
import java.util.UUID

object CharacterCardImporter {
    private const val LIMIT = 8 * 1024 * 1024
    fun read(input: InputStream): PromptGroup {
        val bytes = input.readBytesLimited()
        val png = byteArrayOf(137.toByte(),80,78,71,13,10,26,10)
        val text = if (bytes.take(8).toByteArray().contentEquals(png)) {
            var offset = 8
            var v2: String? = null
            var v3: String? = null
            while (offset + 12 <= bytes.size) {
                val length = (0..3).fold(0L) { n, i -> (n shl 8) or (bytes[offset+i].toLong() and 255) }
                require(length <= LIMIT && offset.toLong()+length+12 <= bytes.size) { "PNG 数据损坏" }
                val type = String(bytes, offset+4, 4, Charsets.US_ASCII)
                if (type == "tEXt") {
                    val chunk = bytes.copyOfRange(offset+8, offset+8+length.toInt())
                    val zero = chunk.indexOf(0.toByte())
                    if (zero > 0) {
                        val key = String(chunk,0,zero,Charsets.US_ASCII)
                        if (key == "chara" || key == "ccv3") {
                            val value = String(Base64.getMimeDecoder().decode(chunk.copyOfRange(zero+1,chunk.size)),Charsets.UTF_8)
                            if (key == "ccv3") v3=value else v2=value
                        }
                    }
                }
                offset += length.toInt()+12
                if (type == "IEND") break
            }
            v3 ?: v2 ?: error("图片中没有 chara/ccv3 角色卡数据")
        } else String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        return parse(text)
    }
    fun parse(text: String): PromptGroup {
        require(text.length <= LIMIT) { "角色卡过大" }
        val root = Json.parseToJsonElement(text).jsonObject
        val spec = (root["spec"] as? JsonPrimitive)?.contentOrNull
        require(spec == null || spec in listOf("chara_card_v2", "chara_card_v3")) { "不支持的角色卡版本" }
        val data = if (spec == null) root else root["data"]?.jsonObject ?: error("缺少角色数据")
        fun field(key: String) = (data[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        require(listOf("name","description","personality","first_mes").any { field(it).isNotBlank() }) { "不是有效的角色卡" }
        val name = field("name").ifBlank { "导入角色" }
        val role = listOf(field("description"),field("personality"),field("scenario")).filter { it.isNotBlank() }.joinToString("\n\n")
        val chat = listOf(field("system_prompt"),field("post_history_instructions"),field("mes_example")).filter { it.isNotBlank() }.joinToString("\n\n")
        return PromptGroup(UUID.randomUUID().toString(),name,role,field("first_mes"),chat,text)
    }
    private fun InputStream.readBytesLimited(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(out.size()+count <= LIMIT) { "角色卡文件不能超过 8 MB" }
            out.write(buffer,0,count)
        }
        return out.toByteArray()
    }
}
