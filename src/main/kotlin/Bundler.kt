package com.jeff.stuff

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import com.util.html.*

import java.nio.charset.StandardCharsets

open class RegMap(val data: ByteArray) {

    fun read8(offset: Int) : Int {
        return ByteBuffer.wrap(data).get(offset).toInt() and 0xFF
    }

    fun read16(offset: Int) : Int {
        return ByteBuffer.wrap(data).getShort(offset).toInt() and 0xFFFF
    }

    fun read32(offset: Int) : Long {
        return ByteBuffer.wrap(data).getInt(offset).toLong() and 0xFFFFFFFF
    }
}

class HdmiFrontEnd(val reg_maps: Map<String, RegMap?>) {
    var tmdsclk: Double = 0.0
    private var totalwidth: Int = 0
    private var totalheight: Int = 0
    private var width: Int = 0
    private var height: Int = 0
    private var v_polarity = 0
    private var h_polarity = 0
    private var interlaced = false
    private val MHZ = 1000000.0
    private val MHZ_FRAC = 128.0
    private var mode : String
    private var tmdspll_lck : String
    private var tmds_clk_a_det : String
    private var vert_f_locked : String
    private var horz_f_locked : String
    private var hdmi_hdcp : String
    private var cable_det : String
    private var irq2_cleared: String
    private var irq3_cleared: String
    private var irq4_cleared: String

    init {
        val inter = reg_maps["hdmi"]!!.read8(0x0B)
        interlaced = ((inter shr 5) and 0x1) == 1

        width = reg_maps["hdmi"]!!.read16(0x7) and 0x00001FFF
        val hsyncfp = reg_maps["hdmi"]!!.read16(0x20)
        val hsyncdura = reg_maps["hdmi"]!!.read16(0x22)
        val hsyncbp = reg_maps["hdmi"]!!.read16(0x24)
        totalwidth = width + hsyncfp + hsyncdura + hsyncbp

        height = reg_maps["hdmi"]!!.read16(0x9) and 0x00001FFF
        val vsyncfp = reg_maps["hdmi"]!!.read16(0x2A)  / 2
        val vsyncdura = reg_maps["hdmi"]!!.read16(0x2E) / 2
        val vsyncbp = reg_maps["hdmi"]!!.read16(0x32) / 2
        totalheight = height + vsyncfp + vsyncdura + vsyncbp

        val freq_reg = reg_maps["hdmi"]!!.read16(0x51)
        val freq_whole = (freq_reg and 0xFF80) shr 0x7
        val freq_frac: Double = (freq_reg and 0x007F).toDouble() / MHZ_FRAC
        tmdsclk = freq_whole + freq_frac

        //polarity
        val auxpol = reg_maps["hdmi"]!!.read8(0x5)
        v_polarity = (auxpol shr 4) and 0x1
        h_polarity = (auxpol shr 5) and 0x1

        //interrupt raw status's
        val irq2rawstatus = reg_maps["io"]!!.read8(0x65)
        val irq3rawstatus = reg_maps["io"]!!.read8(0x6A)
        val irq4rawstatus = reg_maps["io"]!!.read8(0x6F)

        mode = when(((irq2rawstatus shr 0x3) and 0x1)) {
            1 -> "HDMI"
            0 -> "DVI"
            else -> "unknown"
        }

        tmdspll_lck = when(((irq3rawstatus shr 6) and 0x1)) {
            1 -> "locked"
            0 -> "not Locked"
            else -> "Idunnoknow"
        }

        tmds_clk_a_det = when(((irq3rawstatus shr 4) and 0x1)) {
            1 -> "detected"
            0 -> "not detected"
            else -> "Idunnoknow"
        }

        vert_f_locked = when(((irq3rawstatus shr 1) and 0x1)) {
            1 -> "locked"
            0 -> "not locked"
            else -> "Idunnoknow"
        }

        horz_f_locked = when((irq3rawstatus and 0x1)) {
            1 -> "locked"
            0 -> "not locked"
            else -> "Idunnoknow"
        }

        hdmi_hdcp = when(((irq4rawstatus shr 2)and 0x1)) {
            1 -> "encrypted"
            0 -> "not encrypted"
            else -> "Idunnoknow"
        }

        cable_det = when((irq4rawstatus and 0x1)) {
            1 -> "detected"
            0 -> "not detected"
            else -> "Idunnoknow"
        }


        //interrupt status's should be cleared, its possible we bundled while they are being processed
        val irq2status = reg_maps["io"]!!.read8(0x66)
        val irq3status = reg_maps["io"]!!.read8(0x6B)
        val irq4status = reg_maps["io"]!!.read8(0x70)
        irq2_cleared = when {
            irq2status != 0 -> "not cleared"
            else -> "cleared"
        }
        irq3_cleared = when {
            irq3status != 0 -> "not cleared"
            else -> "cleared"
        }
        irq4_cleared = when {
            irq4status != 0 -> "not cleared"
            else -> "cleared"
        }
    }

    fun getpixelclock(color_depth: Double) : Double {
        return (tmdsclk*8.0/color_depth)
    }

    fun getverticalfreq() : Double {
        return MHZ * (getpixelclock(10.0) / (totalwidth * totalheight))
    }

    fun getcolorspace() : String {
        val cs_reg = reg_maps["hdmi"]!!.read8(0x53)
        return when(cs_reg) {
            0 -> "RGB_LIMITED_CS"
            1 -> "RGB_FULL_CS"
            2 -> "YUV_601_CS"
            3 -> "YUV_709_CS"
            4 -> "XVYCC_601_CS"
            5 -> "XVYCC_709_CS"
            6 -> "YUV_601_FULL_CS"
            7 -> "YUV_709_FULL_CS"
            8 -> "SYYC_CS"
            9 -> "ADOBE_YCC_601_CS"
            10 -> "ADOBE_RGB"
            else -> "UKNOWN_CS"
        }
    }

    fun generatereport() : String {
        val report =  html {
            head {
                style {
                    +"table, td { border: 1px solid black; }"
                }
            }
            body {
                h1 { +"HDMI Input Status"}
                svg("75", "75") {
                    line("0", "37", "75", "37", "stroke:rgb(0,0,0);stroke-width:2") {}
                }
                svg("75", "75") {
                    var style: String
                    if(this@HdmiFrontEnd.cable_det == "detected")
                        style = """fill:rgb(0,255,0)"""
                    else
                        style = """fill:rgb(255,0,0)"""
                    rect("75", "75", style) {}
                    text("#000000", "15", "Verdana", "10", "35") {
                        +"cable"
                    }
                    text("#000000", "15", "Verdana", "10", "50") {
                        +"detect"
                    }
                }
                svg("75", "75") {
                    line("0", "37", "75", "37", "stroke:rgb(0,0,0);stroke-width:2") {}
                }
                svg("75", "75") {
                    var style: String
                    if(this@HdmiFrontEnd.tmds_clk_a_det == "detected")
                        style = """fill:rgb(0,255,0)"""
                    else
                        style = """fill:rgb(255,0,0)"""
                    rect("75", "75", style) {}
                    text("#000000", "15", "Verdana", "10", "35") {
                        +"tmds clk"
                    }
                    text("#000000", "15", "Verdana", "10", "50") {
                        +"detect"
                    }
                }
                svg("75", "75") {
                    line("0", "37", "75", "37", "stroke:rgb(0,0,0);stroke-width:2") {}
                }
                svg("75", "75") {
                    var style: String
                    if(this@HdmiFrontEnd.tmdspll_lck == "locked")
                        style = """fill:rgb(0,255,0)"""
                    else
                        style = """fill:rgb(255,0,0)"""
                    rect("75", "75", style) {}
                    text("#000000", "15", "Verdana", "10", "35") {
                        +"tmds pll"
                    }
                    text("#000000", "15", "Verdana", "10", "50") {
                        +"locked"
                    }
                }
                svg("75", "75") {
                    line("0", "37", "75", "37", "stroke:rgb(0,0,0);stroke-width:2") {}
                }
                svg("75", "75") {
                    var style: String
                    if(this@HdmiFrontEnd.horz_f_locked == "locked")
                        style = """fill:rgb(0,255,0)"""
                    else
                        style = """fill:rgb(255,0,0)"""
                    rect("75", "75", style) {}
                    text("#000000", "15", "Verdana", "10", "35") {
                        +"h_filter"
                    }
                    text("#000000", "15", "Verdana", "10", "50") {
                        +"locked"
                    }
                }
                svg("75", "75") {
                    line("0", "37", "75", "37", "stroke:rgb(0,0,0);stroke-width:2") {}
                }
                svg("75", "75") {
                    var style: String
                    if(this@HdmiFrontEnd.vert_f_locked == "locked")
                        style = """fill:rgb(0,255,0)"""
                    else
                        style = """fill:rgb(255,0,0)"""
                    rect("75", "75", style) {}
                    text("#000000", "15", "Verdana", "10", "35") {
                        +"v_filter"
                    }
                    text("#000000", "15", "Verdana", "10", "50") {
                        +"locked"
                    }
                }
                h2 { +"HDMI Input Information"}
                p {
                    +"Details about the HDMI input signal"
                }
                table("width:50%") {
                    tr {
                        td {
                            +"width"
                        }
                        td {
                            +"$width"
                        }
                    }
                    tr {
                        td {
                            +"height"
                        }
                        td {
                            +"$height"
                        }
                    }
                    tr {
                        td {
                            +"vert freq"
                        }
                        td {
                            +"${getverticalfreq()}"
                        }
                    }
                    tr {
                        td {
                            +"colorspace"
                        }
                        td {
                            +"${getcolorspace()}"
                        }
                    }
                    tr {
                        td {
                            +"vertical polarity"
                        }
                        td {
                            +"${v_polarity}"
                        }
                    }
                    tr {
                        td {
                            +"horizontal polarity"
                        }
                        td {
                            +"$h_polarity"
                        }
                    }
                    tr {
                        td {
                            +"interlaced"
                        }
                        td {
                            +"$interlaced"
                        }
                    }
                    tr {
                        td {
                            +"mode"
                        }
                        td {
                            +"$mode"
                        }
                    }
                    tr {
                        td {
                            +"HDCP"
                        }
                        td {
                            +"$hdmi_hdcp"
                        }
                    }
                    tr {
                        td {
                            +"irq level 2 cleared"
                        }
                        td {
                            +"$irq2_cleared"
                        }
                    }
                    tr {
                        td {
                            +"irq level 3 cleared"
                        }
                        td {
                            +"$irq3_cleared"
                        }
                    }
                    tr {
                        td {
                            +"irq level 4 cleared"
                        }
                        td {
                            +"$irq4_cleared"
                        }
                    }
                }
            }
        }
        return report.toString()
    }
}

//a special flatten for flattening special things
fun List<String>.flatten() : String {
    var flat = ""
    for(c in this) {
        flat += c.substringAfter("|").trim() + " "
    }
    return flat.trim()
}


fun process_table(blob: String) : ByteArray {
    //no line can ever be too long
    val raw_values = blob.substring(blob.findLastAnyOf(setOf("-"))!!.first+1).split("\n").flatten().split(" ")
    val data = ByteArray(raw_values.size)
    for((index, value) in raw_values.withIndex()) {
        data[index] = Integer.parseInt(value, 16).toByte()
    }
    return data
}

fun process_reg_file(path: Path) : Map<String, RegMap?> {
    println("The path is $path")

    val data = Files.readAllBytes(path)
    val str =  String(data, Charset.defaultCharset())

    var reg_maps = mutableMapOf<String, RegMap?>("hdmi" to null,
            "io" to null,
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
        reg_maps[header] = RegMap(result)
    }

    return reg_maps
}

fun main(args: Array<String>) {
    println("number of args = ${args.size}")
    val maps = mutableListOf<Map<String, RegMap?>>()
    val bundle_path = Paths.get("./data")
    for(arg in args) {
        println(arg)
        maps.add(process_reg_file(bundle_path.resolve(arg)))
    }

    val fe1 = HdmiFrontEnd(maps[0])

    println("html ${fe1.generatereport()}")
    Files.write(Paths.get("./data/report.html"), fe1.generatereport().toByteArray(StandardCharsets.UTF_8))
}