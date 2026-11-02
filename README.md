# Anxdoid Runtime — SAF image-picker prototype

This branch adds an Android Storage Access Framework (SAF) file picker to the Anxdoid-derived app. The user can select a system image from a document provider and the app stores a persistable read-only URI grant. It does not request broad storage access and does not copy the selected source image into `Android/data` or the app's private `/data` directory.

## Important runtime limitation

This is currently **image selection only**, not a bootable image importer. The existing native Anxdoid launcher expects an extracted ARM64 root filesystem available as ordinary filesystem paths and starts guest binaries using `execve`. A SAF `content://` URI is not a POSIX path and cannot directly be used as that rootfs. Therefore the guest-start button stays disabled rather than implying that selecting an image boots it.

A future bootable implementation needs a safe image parser/importer and a runtime interface that consumes the selected document. If an extracted rootfs is required, Android's ordinary unprivileged app model requires writing runtime files to storage the app can execute/read; SAF alone does not make an arbitrary document provider directory executable. This project will not write to device-wide `/data` or try to bypass Android storage/SELinux restrictions.

## Build

Use the existing Gradle project and **Actions → Build CloudShell APK** workflow. This change should be validated by the workflow before installation on a device.
