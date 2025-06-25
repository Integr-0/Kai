package net.integr.kai.driver

import net.integr.kai.output.OutputBundler
import java.io.BufferedWriter
import java.io.OutputStream

interface ModelDriver {
    fun query(query: String, output: OutputBundler)
}