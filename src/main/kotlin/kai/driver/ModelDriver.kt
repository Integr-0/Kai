package net.integr.kai.driver

import net.integr.kai.output.OutputBundler

interface ModelDriver {
    fun query(query: String, output: OutputBundler)
}