package com.example.ui.navigation

import com.example.data.model.ImageToolType
import com.example.data.model.PdfToolType

object PaperlyRoutes {
    const val MAIN_TABS = "main_tabs"
    const val HOME = "home"
    const val FILES = "files"
    const val SETTINGS = "settings"
    const val TOOL_WORKSPACE = "tool_workspace/{toolType}"
    const val IMAGE_WORKSPACE = "image_workspace/{imageToolType}"
    const val IMAGE_RESULT = "image_result"
    const val MULTI_SHOT_CAMERA = "multi_shot_camera"
    const val DOWNLOAD_READY = "download_ready/{recordId}"
    const val PDF_VIEWER = "pdf_viewer/{recordId}"
    const val EXTERNAL_PDF_VIEWER = "external_pdf_viewer"

    fun toolWorkspace(toolType: PdfToolType): String = "tool_workspace/${toolType.routeArg}"
    fun imageWorkspace(toolType: ImageToolType): String = "image_workspace/${toolType.routeArg}"
    fun downloadReady(recordId: Long): String = "download_ready/$recordId"
    fun pdfViewer(recordId: Long): String = "pdf_viewer/$recordId"
}
