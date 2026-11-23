package com.example.ui.navigation

import com.example.data.model.PdfToolType

object PaperlyRoutes {
    const val HOME = "home"
    const val FILES = "files"
    const val SETTINGS = "settings"
    const val TOOL_WORKSPACE = "tool_workspace/{toolType}"
    const val DOWNLOAD_READY = "download_ready/{recordId}"
    const val PDF_VIEWER = "pdf_viewer/{recordId}"

    fun toolWorkspace(toolType: PdfToolType): String = "tool_workspace/${toolType.routeArg}"
    fun downloadReady(recordId: Long): String = "download_ready/$recordId"
    fun pdfViewer(recordId: Long): String = "pdf_viewer/$recordId"
}
