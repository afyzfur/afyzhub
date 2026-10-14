package com.afyzfur.afyzhub
import org.junit.Test
import org.junit.Assert.*
import com.afyzfur.afyzhub.domain.model.*
import com.afyzfur.afyzhub.ui.settings.CharacterCardImporter
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
class PromptGroupsTest {
 @Test fun switchKeepsData() {
  val p = ApiProfile(id="x",name="test",systemPrompt="role",openingPrompt="hello",chatSettingsPrompt="rules")
  assertEquals("role\n\nrules",p.effectiveSystemPrompt)
  val off=p.copy(promptsEnabled=false)
  assertEquals("",off.effectiveSystemPrompt); assertEquals("",off.effectiveOpening)
  assertEquals("role",off.activePrompt.role)
  assertEquals("hello",off.copy(promptsEnabled=true).effectiveOpening)
 }
 @Test fun groupsPersistAndSwitch() {
  val p=ApiProfile(id="x",name="test",promptGroups=listOf(PromptGroup("a","A",role="one"),PromptGroup("b","B",role="two")),activePromptId="b")
  val saved=Json.decodeFromString<ApiProfile>(Json.encodeToString(p))
  assertEquals("two",saved.effectiveSystemPrompt)
  assertEquals("one",saved.copy(activePromptId="a").effectiveSystemPrompt)
 }
 @Test fun jsonAndPngImport() {
  val text="""{"spec":"chara_card_v2","data":{"name":"Alice","description":"Kind","first_mes":"Hello","system_prompt":"Be brief"}}"""
  val card=CharacterCardImporter.parse(text)
  assertEquals("Alice",card.name);assertEquals("Hello",card.opening);assertEquals("Be brief",card.chat)
  val out=ByteArrayOutputStream();val d=DataOutputStream(out)
  d.write(byteArrayOf(137.toByte(),80,78,71,13,10,26,10))
  val body="chara\u0000".toByteArray()+Base64.getEncoder().encode(text.toByteArray())
  d.writeInt(body.size);d.writeBytes("tEXt");d.write(body);d.writeInt(0)
  d.writeInt(0);d.writeBytes("IEND");d.writeInt(0)
  assertEquals("Alice",CharacterCardImporter.read(out.toByteArray().inputStream()).name)
 }
 @Test fun malformedCardRejected() {
  try { CharacterCardImporter.parse("{}");fail("invalid card accepted") } catch (_: IllegalArgumentException) {}
 }
}
