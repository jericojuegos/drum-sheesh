import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

abstract class CheckMergedManifestPermissions : DefaultTask() {
    @get:InputFiles
    abstract val manifestFiles: ListProperty<RegularFile>

    @get:Input
    abstract val allowedPermissions: SetProperty<String>

    @get:Input
    abstract val prohibitedPermissions: SetProperty<String>

    @TaskAction
    fun checkManifests() {
        val allowed = allowedPermissions.get()
        val prohibited = prohibitedPermissions.get()

        val files = manifestFiles.get()
        if (files.isEmpty()) {
            throw GradleException("FATAL: No manifest files provided to checkMergedManifestPermissions.")
        }

        for (file in files) {
            val manifestFile = file.asFile
            if (!manifestFile.exists()) {
                throw GradleException("FATAL: Expected manifest file does not exist: ${manifestFile.absolutePath}")
            }

            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestFile)
            val permissionNodes = doc.getElementsByTagName("uses-permission")

            for (i in 0 until permissionNodes.length) {
                val element = permissionNodes.item(i) as Element
                val permissionName = element.getAttribute("android:name")
                val toolsNode = element.getAttribute("tools:node")
                if (toolsNode == "remove") continue

                if (prohibited.contains(permissionName)) {
                    throw GradleException(
                        "FATAL: Merged manifest '${manifestFile.name}' contains prohibited permission: $permissionName"
                    )
                }
                if (!allowed.contains(permissionName)) {
                    throw GradleException(
                        "FATAL: Merged manifest '${manifestFile.name}' contains unapproved permission: $permissionName. Allowed: $allowed"
                    )
                }
            }
        }
        logger.lifecycle("✅ Merged manifest permissions verified against allowlist: $allowed")
    }
}

tasks.register<CheckMergedManifestPermissions>("checkMergedManifestPermissions") {
    group = "verification"
    description = "Verifies that Android manifests only declare approved permissions (VIBRATE, INTERNET)."
    dependsOn(":app:processDebugMainManifest")

    manifestFiles.add(
        project(":app").layout.buildDirectory.file("intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml")
    )
    manifestFiles.add(
        project(":app").layout.projectDirectory.file("src/main/AndroidManifest.xml")
    )
    allowedPermissions.set(
        setOf(
            "android.permission.VIBRATE",
            "android.permission.INTERNET",
            "com.drumsheesh.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        )
    )
    prohibitedPermissions.set(
        setOf(
            "android.permission.RECORD_AUDIO",
            "android.permission.CAMERA",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "com.google.android.gms.permission.AD_ID"
        )
    )
}
