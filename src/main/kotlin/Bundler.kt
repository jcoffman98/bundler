package com.jeff.stuff

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths

class RegMap(val data: ByteArray) {

    fun read8(offset: Int) : Byte {
        return ByteBuffer.wrap(data).get(offset)
    }

    fun read16(offset: Int) : Short {
        return ByteBuffer.wrap(data).getShort(offset)
    }

    fun read32(offset: Int) : Int {
        return ByteBuffer.wrap(data).getInt(offset)
    }
}

fun List<String>.flatten() : String {
    var flat = ""
    for(c in this) {
        flat += c.substringAfter("|").trim()
    }
    return flat
}

fun process_table(blob: String?) : ByteArray {
    //no line can ever be too long
    val raw_values = blob!!.substring(blob!!.findLastAnyOf(setOf("-"))!!.first+1).split("\n").flatten().split(" ")
    val data = ByteArray(raw_values.size)
    for((index, value) in raw_values.withIndex()) {
        data[index] = Integer.parseInt(value, 16).toByte()
    }
    return data
}

fun main(args: Array<String>) {

    val bundle_path = Paths.get("./data")

    println("The path is ${bundle_path.resolve("hdmi_rx_reg.txt")}")

    val data = Files.readAllBytes(bundle_path.resolve("hdmi_rx_reg.txt"))
    val str =  String(data, Charset.defaultCharset())

    var reg_maps = mutableMapOf<String, RegMap?>("hdmi" to null,
                                                  "rep" to null,
                                                 "edid" to null,
                                                   "if" to null,
                                                  "cec" to null,
                                                   "cp" to null,
                                                 "dpll" to null)

    //There should be an entry for each type
    for(header in reg_maps.keys) {
        val substr = str.substring( str.findAnyOf(listOf(header))?.first!!)
        val endex = substr.findAnyOf(reg_maps.keys.filter { s -> s != header})?.let {
            it.first
        } ?: substr.length
        val result = process_table(substr.substring(substr.findAnyOf(listOf(header))?.first!!, endex))
        println("$header(byte) -> ${result}")
        reg_maps[header] = RegMap(result)
    }

    println(reg_maps["hdmi"]?.read8(0))
    println(reg_maps["hdmi"]?.read16(4))
}