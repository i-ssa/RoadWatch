package com.example.roadwatch.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import ai.onnxruntime.*
import org.json.JSONArray
import org.json.JSONObject
import java.nio.FloatBuffer

data class Detection(val x: Float, val y: Float, val width: Float, val height: Float, val confidence: Float) {
    fun json() = JSONObject().put("x",x).put("y",y).put("width",width).put("height",height).put("confidence",confidence).put("label","pothole")
}

/** YOLO11 single-class FP32 export: RGB NCHW [1,3,640,640], output [1,5,8400]. */
class PotholeDetector(context: Context) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
    private val session = environment.createSession(context.assets.open("pothole.onnx").use { it.readBytes() }, options)
    fun detect(source: Bitmap): List<Detection> {
        val scale = minOf(640f/source.width,640f/source.height)
        val width = (source.width*scale).toInt(); val height = (source.height*scale).toInt()
        val left = (640-width)/2f; val top = (640-height)/2f
        val input = Bitmap.createBitmap(640,640,Bitmap.Config.ARGB_8888)
        Canvas(input).apply {
            drawColor(Color.rgb(114,114,114))
            drawBitmap(source,null,RectF(left,top,left+width,top+height),Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val pixels = IntArray(640*640); input.getPixels(pixels,0,640,0,0,640,640); input.recycle()
        val data = FloatArray(3*pixels.size)
        pixels.forEachIndexed { i,p -> data[i]=((p shr 16) and 255)/255f; data[i+pixels.size]=((p shr 8) and 255)/255f; data[i+2*pixels.size]=(p and 255)/255f }
        OnnxTensor.createTensor(environment,FloatBuffer.wrap(data),longArrayOf(1,3,640,640)).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val output = (result[0].value as Array<Array<FloatArray>>)[0]
                check(output.size == 5) { "Unexpected detector output" }
                val candidates = output[0].indices.filter { output[4][it]>=0.25f }.map { i ->
                    val x1=((output[0][i]-output[2][i]/2-left)/scale/source.width).coerceIn(0f,1f)
                    val y1=((output[1][i]-output[3][i]/2-top)/scale/source.height).coerceIn(0f,1f)
                    val x2=((output[0][i]+output[2][i]/2-left)/scale/source.width).coerceIn(0f,1f)
                    val y2=((output[1][i]+output[3][i]/2-top)/scale/source.height).coerceIn(0f,1f)
                    Detection(x1,y1,x2-x1,y2-y1,output[4][i])
                }.filter { it.width>0 && it.height>0 }.sortedByDescending { it.confidence }
                return suppress(candidates)
            }
        }
    }
    override fun close() { session.close(); options.close() }
    companion object {
        fun suppress(candidates: List<Detection>): List<Detection> {
            val kept = mutableListOf<Detection>()
            for(box in candidates) {
                if(kept.none { other ->
                    val overlap = (minOf(box.x+box.width,other.x+other.width)-maxOf(box.x,other.x)).coerceAtLeast(0f)*
                        (minOf(box.y+box.height,other.y+other.height)-maxOf(box.y,other.y)).coerceAtLeast(0f)
                    overlap/(box.width*box.height+other.width*other.height-overlap)>0.45f
                }) kept.add(box)
                if(kept.size == 100) break
            }
            return kept
        }
        fun json(boxes: List<Detection>) = JSONArray().apply { boxes.forEach { put(it.json()) } }
    }
}
