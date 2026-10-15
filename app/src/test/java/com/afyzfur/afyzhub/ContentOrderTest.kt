package com.afyzfur.afyzhub
import com.afyzfur.afyzhub.domain.model.*
import org.junit.Assert.*
import org.junit.Test
class ContentOrderTest {
 @Test fun interleavedSegmentsRetainPositions() {
  val blocks = parseContentBlocks("<think>before</think><web_search>query</web_search>answer1<think>after</think><web_search>query</web_search>answer2")
  assertEquals(listOf(ContentBlock.Think("before",false),ContentBlock.Search("query"),ContentBlock.Answer("answer1"),ContentBlock.Think("after",false),ContentBlock.Search("query"),ContentBlock.Answer("answer2")), blocks)
 }
 @Test fun sourcesDoNotErasePreSearchThinking() {
  val blocks = parseContentBlocks("<think>before</think><web_search>query</web_search><sources>title :: https://example.com</sources><think>after</think>answer")
  assertEquals(4,blocks.size)
  assertEquals(ContentBlock.Think("before",false),blocks[0])
  assertEquals(ContentBlock.Search("query"),blocks[1])
  assertEquals(ContentBlock.Think("after",false),blocks[2])
  assertEquals(ContentBlock.Answer("answer"),blocks[3])
 }
}